package org.clever.nashorn.websocket;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.clever.common.utils.exception.ExceptionUtils;
import org.clever.common.utils.mapper.JacksonMapper;
import org.clever.nashorn.dto.response.ConsoleLogRes;
import org.clever.nashorn.model.WebSocketTaskReq;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 任务抽象
 * <p>
 * 作者： lzw<br/>
 * 创建时间：2018-01-07 18:42 <br/>
 *
 * @param <T> 请求消息的类型
 */
@SuppressWarnings("WeakerAccess")
@Slf4j
public abstract class Task<T extends WebSocketTaskReq> {

    /**
     * 执行WebSocket任务的线程池
     */
    public static final ThreadPoolExecutor Task_Thread_Pool = new ThreadPoolExecutor(
            1,                          // 核心线程数，即使空闲也仍保留在池中的线程数
            64,                     // 最大线程数
            30, TimeUnit.SECONDS,      // 保持激活时间，当线程数大于核心数时，这是多余的空闲线程在终止之前等待新任务的最大时间
            new ArrayBlockingQueue<>(512),  // 当线程池的任务缓存队列容量
            new ThreadPoolExecutor.AbortPolicy()    // 当线程池的任务缓存队列已满，并且线程池中的线程数目达到最大线程数，如果还有任务到来就会采取任务拒绝策略
    );

    static {
        // 优雅关闭线程池
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Task_Thread_Pool.shutdownNow();
                log.debug("WebSocket任务线程池关闭成功");
            } catch (Throwable e) {
                log.warn("关闭WebSocket任务线程池异常", e);
            }
        }));
    }

    /**
     * 连接当前任务的Session集合<br />
     * 一个Task对应多个session，一个session只能对应一个Task
     */
    private final CopyOnWriteArraySet<WebSocketSession> Session_Set = new CopyOnWriteArraySet<>();
    /**
     * 正在运行的任务数量
     */
    private final AtomicLong runningTaskCount = new AtomicLong(0);
    /**
     * 正在运行的任务数量 + 排队中的任务数量
     */
    private final AtomicLong totalTaskCount = new AtomicLong(0);
    /**
     * 任务运行超时时间单位秒(小于等于0表示不超时)
     */
    @Getter
    protected long runTimeOut = 60;

    /**
     * 当前任务ID(全局唯一)
     */
    @Getter
    private final String taskId;
    /**
     * 任务类型
     */
    @Getter
    private final TaskType taskType;
    /**
     * 当前任务是否已经停止
     */
    @Getter
    private boolean stop = false;
    /**
     * 任务是否已经启动
     */
    @Getter
    private boolean start = false;
    /**
     * 任务启动时间
     */
    @Getter
    private Long startTime;

    /**
     * @param taskId   当前任务ID(全局唯一)
     * @param taskType 当前任务类型
     */
    public Task(String taskId, TaskType taskType) {
        this.taskId = taskId;
        this.taskType = taskType;
    }

    /**
     * 正在运行的任务数量
     */
    public long getRunningTaskCount() {
        return runningTaskCount.get();
    }

    /**
     * 正在运行的任务数量 + 排队中的任务数量
     */
    public long getTotalTaskCount() {
        return totalTaskCount.get();
    }

    /**
     * 增加一个WebSocketSession到当前任务
     */
    public void addWebSocketSession(WebSocketSession session) {
        Session_Set.add(session);
    }

    /**
     * 从当前任务移除一个WebSocketSession
     */
    private boolean removeWebSocketSession(WebSocketSession session) {
        if (session == null) {
            return true;
        }
        WebSocketSession rm = Session_Set.stream().filter(s -> Objects.equals(s.getId(), session.getId())).findFirst().orElse(null);
        return rm != null && Session_Set.remove(rm);
    }

    /**
     * 判断Session是否已经存在
     */
    public boolean contains(WebSocketSession session) {
        return Session_Set.contains(session);
    }

    /**
     * 返回连接当前任务的Session数量
     */
    public int getWebSocketSessionSize() {
        return Session_Set.size();
    }

    /**
     * 发送消息到指定的客户端
     *
     * @param session WebSocket连接
     * @param object  消息对象
     */
    private void sendMessage(WebSocketSession session, Object object) {
        TextMessage textMessage = new TextMessage(JacksonMapper.nonEmptyMapper().toJson(object));
        try {
            session.sendMessage(textMessage);
        } catch (Throwable e) {
            log.error("[ContainerLogTask] 发送任务结束消息异常", e);
        }
    }

    /**
     * 等待所有的连接关闭(会阻塞当前线程)
     */
    protected void awaitAllSessionClose() {
        while (getWebSocketSessionSize() > 0) {
            try {
                Thread.sleep(1000);
                // 移除关闭了的Session
                removeCloseSession();
            } catch (InterruptedException e) {
                log.info("休眠失败", e);
            }
        }
    }

    /**
     * 发送消息到所有的客户端
     *
     * @param object 消息对象
     */
    protected void sendMessage(Object object) {
        Set<WebSocketSession> rmSet = new HashSet<>();
        for (WebSocketSession session : Session_Set) {
            if (!session.isOpen()) {
                rmSet.add(session);
                continue;
            }
            sendMessage(session, object);
        }
        // 移除关闭了的Session
        Session_Set.removeAll(rmSet);
    }

    /**
     * 关闭所有的 WebSocketSession
     */
    protected void closeAllSession() {
        for (WebSocketSession session : Session_Set) {
            WebSocketCloseSessionUtils.closeSession(session);
        }
        Session_Set.clear();
    }

    /**
     * 移除所有已经关闭了 WebSocketSession
     */
    protected void removeCloseSession() {
        // 移除关闭了的Session
        Set<WebSocketSession> rmSet = new HashSet<>();
        for (WebSocketSession session : Session_Set) {
            if (!session.isOpen()) {
                rmSet.add(session);
            }
        }
        Session_Set.removeAll(rmSet);
    }

    /**
     * 开始任务<br />
     *
     * @param message 请求消息
     * @param verify  消息数据校验结果
     */
    public void start(T message, boolean verify) {
        if (start) {
            throw new RuntimeException("任务已经启动了");
        }
        start = true;
        startTime = System.currentTimeMillis();
        doStart(message, false);
    }

    /**
     * 关闭任务<br />
     * 释放资源
     */
    public void stop() {
        stop = true;
        closeAllSession();
        doStop();
    }

    /**
     * 异步执行任务
     *
     * @param task 异步任务
     * @return 异步执行任务成功返回true
     */
    protected boolean execTask(DoTask task) {
        try {
            Task_Thread_Pool.execute(() -> {
                try {
                    runningTaskCount.incrementAndGet();
                    task.runTask();
                } catch (Throwable e) {
                    sendMessage(ConsoleLogRes.newError(ExceptionUtils.getStackTraceAsString(e), null));
                } finally {
                    totalTaskCount.decrementAndGet();
                    runningTaskCount.decrementAndGet();
                }
            });
            totalTaskCount.incrementAndGet();
            return true;
        } catch (Throwable e) {
            sendMessage(ConsoleLogRes.newError(ExceptionUtils.getStackTraceAsString(e), null));
            return false;
        }
    }

    // ----------------------------------------------------------------------------------------------------------- 需要子类实现的方法

    /**
     * 任务开始执行任务功能
     *
     * @param message 请求消息
     * @param verify  消息数据校验结果
     */
    protected abstract void doStart(T message, boolean verify);

    /**
     * 结束任务释放资源
     */
    protected abstract void doStop();

    /**
     * 处理请求消息
     *
     * @param session 会话
     * @param message 请求消息
     * @param verify  消息数据校验结果
     */
    protected abstract void handleMessage(WebSocketSession session, T message, boolean verify);

    // -----------------------------------------------------------------------------------------------------------

    /**
     * 运行任务接口
     */
    public static interface DoTask {

        /**
         * 具体的执行任务
         */
        void runTask() throws Throwable;
    }
}
