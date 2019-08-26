package org.clever.nashorn.internal;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.clever.nashorn.module.Module;
import org.clever.nashorn.utils.StrFormatter;

/**
 * 作者：lizw <br/>
 * 创建时间：2019/08/22 09:30 <br/>
 */
@SuppressWarnings("WeakerAccess")
public abstract class AbstractConsole implements Console {
    /**
     * 输出最大长度
     */
    public static final int Max_Len = 1024 * 8;
    /**
     * 日志溢出时的后缀
     */
    public static final String Overflow_Suffix = "...";

    @Getter
    private final String filePath;
    @Getter
    private final String fileName;

    /**
     * @param filePath 文件路径
     * @param fileName 文件名称
     */
    public AbstractConsole(String filePath, String fileName) {
        this.filePath = filePath;
        this.fileName = fileName;
    }

    /**
     * 创建 root Console
     *
     * @param filePath root文件路径
     */
    public AbstractConsole(String filePath) {
        this(filePath, Module.Root_Filename);
    }

    /**
     * 根据日志输出参数得到日志字符串
     */
    protected String logString(Object... args) {
        if (args == null || args.length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(args.length * 32);
        for (Object arg : args) {
            String str = StrFormatter.toString(arg);
            sb.append(overflow(str));

        }
        return overflow(sb.toString());
    }

    /**
     * 字符串溢出处理
     */
    @SuppressWarnings("ConstantConditions")
    protected String overflow(String str) {
        if (StringUtils.isNotBlank(str) && Max_Len < str.length()) {
            int end = Max_Len - Overflow_Suffix.length();
            if (end < 0) {
                end = 0;
            }
            str = str.substring(0, end) + Overflow_Suffix;
        }
        return str;
    }

    /**
     * 日志格式化处理
     */
    protected String format(final String format, final Object... argArray) {
        return StrFormatter.format(format, argArray);
    }
}
