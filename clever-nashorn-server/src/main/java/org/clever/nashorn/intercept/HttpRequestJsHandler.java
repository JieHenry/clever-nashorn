package org.clever.nashorn.intercept;

import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.clever.common.utils.mapper.JacksonMapper;
import org.clever.common.utils.spring.SpringContextHolder;
import org.clever.nashorn.ScriptModuleInstance;
import org.clever.nashorn.cache.JsCodeFileCache;
import org.clever.nashorn.cache.MemoryJsCodeFileCache;
import org.clever.nashorn.folder.DatabaseFolder;
import org.clever.nashorn.folder.Folder;
import org.clever.nashorn.internal.CommonUtils;
import org.clever.nashorn.internal.Console;
import org.clever.nashorn.internal.LogConsole;
import org.clever.nashorn.module.cache.MemoryModuleCache;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 作者：lizw <br/>
 * 创建时间：2019/09/20 16:21 <br/>
 */
@Slf4j
public class HttpRequestJsHandler implements HandlerInterceptor {
    /**
     * 请求支持的后缀
     */
    private static final Set<String> Support_Suffix = new HashSet<String>() {{
        add("");
        add(".json");
        add(".action");
    }};
    /**
     * JS处理请求的方法名
     */
    private static final String Handler_Method = "service";
    /**
     * 处理请求脚本的文件名称
     */
    private static final String Handler_File_Name = "controller";
    /**
     * 响应数据序列化
     */
    private JacksonMapper jacksonMapper;
    /**
     * 业务类型
     */
    private final String bizType;
    /**
     * 代码分组
     */
    private final String groupName;
    private final Map<String, Object> context = new HashMap<>(1);
    private ScriptModuleInstance scriptModuleInstance;

    public HttpRequestJsHandler(final String bizType, final String groupName) {
        this.bizType = bizType;
        this.groupName = groupName;
    }

    private synchronized void init() {
        if (scriptModuleInstance != null) {
            return;
        }
        ObjectMapper objectMapper = SpringContextHolder.getBean(ObjectMapper.class);
        jacksonMapper = new JacksonMapper(objectMapper);
        // 设置context内容
        context.put("CommonUtils", CommonUtils.Instance);
        // 初始化ScriptModuleInstance
        JsCodeFileCache jsCodeFileCache = MemoryJsCodeFileCache.getInstance();
        Folder rootFolder = new DatabaseFolder(bizType, groupName, jsCodeFileCache);
        Console console = new LogConsole("/");
        MemoryModuleCache moduleCache = new MemoryModuleCache();
        scriptModuleInstance = new ScriptModuleInstance(rootFolder, moduleCache, console, context);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        init();
        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        boolean supportUri = false;
        for (String suffix : Support_Suffix) {
            if (StringUtils.isBlank(suffix)) {
                supportUri = true;
                continue;
            }
            if (requestUri.endsWith(suffix)) {
                supportUri = true;
                requestUri = requestUri.substring(0, requestUri.length() - suffix.length());
                break;
            }
        }
//        // TODO 问题
//        if (supportUri) {
//            // ...(requestUri)/post_controller
//            requestUri = requestUri + "/" + method.toLowerCase() + "_" + Handler_File_Name;
//        }
//        // TODO 问题
//        if (StringUtils.isBlank(requestUri) || StringUtils.isBlank(scriptModuleInstance.getFolder().getFileContent(requestUri))) {
//            return true;
//        }
        final ScriptObjectMirror jsHandler = scriptModuleInstance.useJs(requestUri);
        Object handlerObject = jsHandler.getMember(Handler_Method);
        if (!(handlerObject instanceof ScriptObjectMirror)) {
            return true;
        }
        ScriptObjectMirror handlerFunction = (ScriptObjectMirror) handlerObject;
        if (!handlerFunction.isFunction()) {
            return true;
        }
        if (handler instanceof HandlerMethod) {
            log.warn("js请求处理函数功能被原生SpringMvc功能覆盖 | {}", requestUri);
            response.setHeader("use-http-request-js-handler-be-override", requestUri);
            return true;
        }
        if (!(handler instanceof ResourceHttpRequestHandler)) {
            log.warn("出现意外的handler | {}", handler.getClass());
            return true;
        }
        // 使用js代码处理请求
        Object result = jsHandler.callMember(Handler_Method);
        if (result != null) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().println(jacksonMapper.toJson(result));
        }
        return false;
    }

//    @Override
//    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
//        log.info("=================================================== postHandle");
//    }
//
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
//        log.info("=================================================== afterCompletion");
//    }
}
