package org.clever.nashorn.test;

import lombok.extern.slf4j.Slf4j;
import org.clever.nashorn.utils.JsCodeFilePathUtils;
import org.junit.Test;

/**
 * 作者：lizw <br/>
 * 创建时间：2019/08/27 17:42 <br/>
 */
@Slf4j
public class JsCodeFilePathUtilsTest {

    @Test
    public void t01() {
        log.info("{}", JsCodeFilePathUtils.getParentPath(""));
        log.info("{}", JsCodeFilePathUtils.getParentPath("/"));
        log.info("{}", JsCodeFilePathUtils.getParentPath("/public"));
        log.info("{}", JsCodeFilePathUtils.getParentPath("/public/tmp.js"));
    }
}
