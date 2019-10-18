package org.clever.nashorn.websocket;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.clever.common.utils.exception.ExceptionUtils;
import org.clever.common.utils.reflection.ReflectionsUtils;
import org.clever.common.utils.tuples.TupleSeven;
import org.clever.common.utils.validator.BaseValidatorUtils;
import org.clever.common.utils.validator.ValidatorFactoryUtils;
import org.clever.nashorn.dto.response.ConsoleLogRes;
import org.clever.nashorn.dto.response.TaskManagerRes;
import org.clever.nashorn.model.TaskInfo;
import org.clever.nashorn.model.WebSocketTaskReq;
import org.clever.nashorn.websocket.utils.ThreadPoolUtils;
import org.clever.nashorn.websocket.utils.WebSocketCloseSessionUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import javax.validation.ConstraintViolationException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * WebSocket 请求处理类
 * <p>
 * 作者： lzw<br/>
 * 创建时间：2018-01-07 19:04 <br/>
 *
 * @param <T> 请求消息的类型
 * @param <K> WebSocket任务类型
 */
@SuppressWarnings({"WeakerAccess", "FieldCanBeLocal"})
@Slf4j
public abstract class Handler<T extends WebSocketTaskReq, K extends Task<T>> extends AbstractWebSocketHandler {

    /**
     * 所有的任务 Map<taskId, Task> <br />
     * 一个Task对应多个session，一个session只能对应一个Task
     */
    private static final ConcurrentHashMap<String, Task> TASK_MAP = new ConcurrentHashMap<>();
    private static long Last_Debug_Text = System.currentTimeMillis();
    private static long Debug_Interval = 1000 * 30;

    static {
        // 守护线程
        Thread thread = new Thread(() -> {
            while (true) {
                clearTask(false);
                try {
                    Thread.sleep(1000 * 3);
                } catch (Throwable e) {
                    log.error("[WebSocket] 休眠失败", e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public static TaskManagerRes clearTask(boolean force) {
        final long currentTime = System.currentTimeMillis();
        List<String> rmList = new ArrayList<>();
        int allSessionCount = 0;
        for (ConcurrentHashMap.Entry<String, Task> entry : TASK_MAP.entrySet()) {
            String key = entry.getKey();
            Task task = entry.getValue();
            allSessionCount += task.getWebSocketSessionSize();
            // 任务已停止或者任务已经超时
            boolean timeOut = task.getStartTime() != null
                    && task.getRunTimeOut() > 0
                    && ((currentTime - task.getStartTime()) / 1000) >= task.getRunTimeOut();
            if (task.isStop() || timeOut) {
                try {
                    if (timeOut) {
                        task.sendMessage(ConsoleLogRes.newError("[执行超时] - 服务端主动关闭", null));
                        Thread.sleep(5);
                    }
                    task.stop();
                    rmList.add(key);
                } catch (Throwable e) {
                    log.error(String.format("[WebSocket] 释放%1$s任务失败", task.getClass().getSimpleName()), e);
                }
            }
        }
        for (String key : rmList) {
            TASK_MAP.remove(key);
        }
        // 打印日志
        TaskManagerRes res = null;
        if (force || (currentTime - Last_Debug_Text) > Debug_Interval) {
            res = getText(allSessionCount, rmList);
        }
        if (res != null && log.isDebugEnabled()) {
            log.debug(res.getSummaryText());
            Last_Debug_Text = System.currentTimeMillis();
        }
        return res;
    }

    private static TaskManagerRes getText(int allSessionCount, List<String> rmList) {
        final String tab = "\t";
        final String enter = "\r\n";
        final String line1 = "=======================================================================================================================================================================";
        final String line2 = "-----------------------------------------------------------------------------------------------------------------------------------------------------------------------";
        Map<String, StringBuilder> taskInfoMap = new HashMap<>(TASK_MAP.size());
        StringBuilder summary = new StringBuilder(256);
        summary.append(enter).append(line1).append(enter);
        summary.append(String.format(
                " [WebSocket] -> ConnectorCount=%-5s   | TaskCount=%-5s          | RemoveTaskCount=%-5s",
                allSessionCount,
                TASK_MAP.size(),
                rmList.size())
        ).append(enter);
        // summary.append(line2).append(enter);
        TupleSeven<Integer, Integer, Integer, Integer, Integer, Integer, Integer> tupleSeven = ThreadPoolUtils.getThreadPoolInfo();
        summary.append(String.format(
                "[ThreadPool] -> CorePoolSize=%-5s     | MaximumPoolSize=%-5s    | PoolSize=%-5s | Queue.size=%s ||-> TaskCount=%s | ActiveCount=%s |  CompletedTaskCount=%s",
                tupleSeven.getValue1(),
                tupleSeven.getValue2(),
                tupleSeven.getValue3(),
                tupleSeven.getValue4(),
                tupleSeven.getValue5(),
                tupleSeven.getValue6(),
                tupleSeven.getValue7()
        )).append(enter);
        List<TaskInfo> taskInfoList = new ArrayList<>(TASK_MAP.size());
        for (Map.Entry<String, Task> entry : TASK_MAP.entrySet()) {
            String taskId = entry.getKey();
            Task task = entry.getValue();
            StringBuilder sb = taskInfoMap.computeIfAbsent(taskId, s -> new StringBuilder());
            sb.append(String.format("[Task] -> [%s]", taskId)).append(enter);
            sb.append(tab).append("            TaskType：").append(task.getTaskType()).append(enter);
            sb.append(tab).append("WebSocketSessionSize：").append(task.getWebSocketSessionSize()).append(enter);
            sb.append(tab).append("    RunningTaskCount：").append(task.getRunningTaskCount()).append(enter);
            sb.append(tab).append("      TotalTaskCount：").append(task.getTotalTaskCount()).append(enter);
            // TaskInfo
            TaskInfo taskInfo = new TaskInfo();
            taskInfo.setTaskId(taskId);
            taskInfo.setTaskType(String.valueOf(task.getTaskType()));
            taskInfo.setWebSocketSessionSize(task.getWebSocketSessionSize());
            taskInfo.setRunningTaskCount(task.getRunningTaskCount());
            taskInfo.setTotalTaskCount(task.getTotalTaskCount());
            taskInfoList.add(taskInfo);
        }
        // 组装返回数据
        if (taskInfoMap.size() <= 0) {
            summary.append(line1).append(enter);
        } else {
            summary.append(line2).append(enter);
        }
        StringBuilder text = new StringBuilder();
        text.append(summary);
        int index = 0;
        for (StringBuilder sb : taskInfoMap.values()) {
            index++;
            text.append(sb);
            if (index >= taskInfoMap.values().size()) {
                text.append(line1).append(enter);
            } else {
                text.append(line2).append(enter);
            }
        }
        TaskManagerRes res = new TaskManagerRes();
        res.setSummaryText(text.toString());
        res.setAllWebSocketSessionSize(allSessionCount);
        res.setTaskCount(TASK_MAP.size());
        res.setRemoveTaskCount(rmList.size());
        res.setSumCorePoolSize(tupleSeven.getValue1());
        res.setSumMaximumPoolSize(tupleSeven.getValue2());
        res.setSumPoolSize(tupleSeven.getValue3());
        res.setSumQueueSize(tupleSeven.getValue4());
        res.setSumTaskCount(tupleSeven.getValue5());
        res.setSumActiveCount(tupleSeven.getValue6());
        res.setSumCompletedTaskCount(tupleSeven.getValue7());
        res.setTaskInfoList(taskInfoList);
        return res;
    }

    public static TaskManagerRes stopTask(String taskId) throws InterruptedException {
        Task task = TASK_MAP.get(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        task.sendMessage(ConsoleLogRes.newError("[管理员强制停止任务] - 服务端主动关闭", null));
        Thread.sleep(5);
        task.stop();
        return clearTask(true);
    }

    /**
     * 请求消息的类型 T 的具体类型
     */
    @Getter
    protected final Class<T> clazzByT = ReflectionsUtils.getClassGenricType(this.getClass());

    /**
     * 获取所有的任务(Task)
     */
    private static Collection<Task> getAllTask() {
        return TASK_MAP.values();
    }

    /**
     * 根据taskId获取 Task对象
     */
    public static Task getTaskByTaskId(String taskId) {
        return TASK_MAP.get(taskId);
    }

    /**
     * 添加任务 <br/>
     * 把session与task关联起来 <br/>
     */
    private void putTask(Task task, WebSocketSession session) {
        TASK_MAP.put(task.getTaskId(), task);
        task.addWebSocketSession(session);
    }

    /**
     * 获取任务的总数量
     */
    private static int getTaskCount() {
        return TASK_MAP.size();
    }

    /**
     * 获取任务的总数量
     */
    private static long getTaskCount(TaskType taskType) {
        return TASK_MAP.values().stream().filter(task -> task.getTaskType().equals(taskType)).count();
    }

    /**
     * 支持部分消息
     */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * 1.发送一个错误消息 <br/>
     * 2.服务端主动关闭连接 <br/>
     *
     * @param errorMessage 错误消息
     * @param close        是否需要关闭连接
     */
    protected void sendErrorMessage(WebSocketSession session, Object errorMessage, boolean close) {
        if (!session.isOpen()) {
            return;
        }
        TextMessage textMessage = new TextMessage(Task.Jackson_Mapper.toJson(errorMessage));
        try {
            session.sendMessage(textMessage);
        } catch (Throwable e) {
            throw ExceptionUtils.unchecked(e);
        }
        if (close) {
            // 关闭连接
            WebSocketCloseSessionUtils.closeSession(session);
        }
    }

    /**
     * 转换消息
     */
    private T convert(TextMessage message) {
        String content = message.getPayload();
        if (StringUtils.isBlank(content)) {
            return null;
        }
        T msg = null;
        try {
            msg = Task.Jackson_Mapper.fromJson(message.getPayload(), clazzByT);
        } catch (Throwable e) {
            log.error("请求消息转换失败", e);
        }
        return msg;
    }

    /**
     * 查询session对于的任务
     */
    @SuppressWarnings("unchecked")
    private K getTaskBySession(WebSocketSession session) {
        Task existsTask = null;
        for (Task task : getAllTask()) {
            if (task.contains(session)) {
                existsTask = task;
                break;
            }
        }
        return (K) existsTask;
    }

    /**
     * 建立连接后
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[建立连接] SessionId={}", session.getId());
    }

    /**
     * 消息传输错误处理
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        K task = getTaskBySession(session);
        if (!session.isOpen()) {
            log.error("[消息传输错误] SessionId={} | TaskId={}", session.getId(), task == null ? "" : task.getTaskId());
            return;
        }
        log.error("[消息传输错误] SessionId={} | TaskId={}", session.getId(), task == null ? "" : task.getTaskId(), exception);
    }

    /**
     * 关闭连接后
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        K task = getTaskBySession(session);
        log.info("[关闭连接] SessionId={} | TaskId={}", session.getId(), task == null ? "" : task.getTaskId());
        WebSocketCloseSessionUtils.closeSession(session);
        if (task != null) {
            task.removeCloseSession();
        }
    }

    /**
     * 消息处理，在客户端通过 WebSocket API 发送的消息会经过这里，然后进行相应的处理
     */
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            doHandleTextMessage(session, message);
        } catch (Throwable e) {
            sendErrorMessage(session, ConsoleLogRes.newError(ExceptionUtils.getStackTraceAsString(e), null), true);
        }
    }

    private void doHandleTextMessage(WebSocketSession session, TextMessage message) {
        K task = getTaskBySession(session);
        T msg = convert(message);
        if (msg == null) {
            return;
        }
        boolean verify = false;
        try {
            BaseValidatorUtils.validateThrowException(ValidatorFactoryUtils.getHibernateValidator(), msg);
            verify = true;
        } catch (ConstraintViolationException ignored) {
            // 请求数据校验不通过
        } catch (Throwable e) {
            sendErrorMessage(session, ConsoleLogRes.newError("请求消息校验失败", null), true);
            log.error("请求消息校验失败", e);
        }
        if (task == null) {
            // 第一次处理消息
            if (Objects.equals(msg.getType(), WebSocketTaskReq.Type_Join_Task)) {
                // 将当前session加入到Task
                if (StringUtils.isNotBlank(msg.getTaskId())) {
                    Task tmpTask = getTaskByTaskId(msg.getTaskId());
                    if (tmpTask != null) {
                        putTask(tmpTask, session);
                    } else {
                        sendErrorMessage(session, ConsoleLogRes.newError("请求TaskId不存在或者Task已经结束", null), true);
                    }
                } else {
                    sendErrorMessage(session, ConsoleLogRes.newError("请求TaskId为空", null), true);
                }
                return;
            }
            // 创建任务
            K newTask;
            try {
                newTask = creatTask(session, msg, verify);
            } catch (Throwable e) {
                sendErrorMessage(session, ConsoleLogRes.newError(ExceptionUtils.getStackTraceAsString(e), null), true);
                return;
            }
            if (newTask != null) {
                log.info("[创建任务成功] SessionId={} | TaskId={}", session.getId(), newTask.getTaskId());
                putTask(newTask, session);
                if (!newTask.isStart()) {
                    try {
                        newTask.start(msg, verify);
                        log.info("[启动任务成功] SessionId={} | TaskId={}", session.getId(), newTask.getTaskId());
                    } catch (Throwable e) {
                        sendErrorMessage(session, ConsoleLogRes.newError(ExceptionUtils.getStackTraceAsString(e), null), true);
                        newTask.stop();
                    }
                }
            }
            return;
        }
        // 处理请求消息
        try {
            task.handleMessage(session, msg, verify);
        } catch (Throwable e) {
            sendErrorMessage(session, ConsoleLogRes.newError(ExceptionUtils.getStackTraceAsString(e), null), false);
        }
    }

    // ----------------------------------------------------------------------------------------------------------- 需要子类实现的方法

    /**
     * 客户段连接成功后第一次处理请求消息(用于创建Task)
     *
     * @param session 会话
     * @param message 请求消息
     * @param verify  消息数据校验结果
     */
    public abstract K creatTask(WebSocketSession session, T message, boolean verify);
}
