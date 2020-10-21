package org.clever.nashorn.test;

import lombok.extern.slf4j.Slf4j;
import org.clever.nashorn.utils.JsCodeFilePathUtils;
import org.junit.Test;

import java.util.Arrays;

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

    @Test
    public void to2() {
        String requestUri = "/aaa/bbb/ccc/ddd/eee/fff1.json";
        String suffix = ".json";
        requestUri = requestUri.substring(0, requestUri.length() - suffix.length());
        log.info("{}", requestUri);
    }

    @Test
    public void t03() {
        final String requestUri = "/aaa/bbb/ccc/ddd/eee/fff1@mmm.json";
        String tmp = requestUri.substring(requestUri.lastIndexOf("/"));
        String[] arr = tmp.split("@");
        log.info("{}", Arrays.toString(arr));

        log.info("{}", requestUri.substring(0, requestUri.length() - (arr[1].length() + 1)));
    }
}
