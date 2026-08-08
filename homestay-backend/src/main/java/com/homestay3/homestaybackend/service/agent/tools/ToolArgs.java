package com.homestay3.homestaybackend.service.agent.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 工具参数解析（LLM 给出的 JSON 参数值类型不稳定，统一做宽容转换）
 */
final class ToolArgs {

    private ToolArgs() {
    }

    static String toStr(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    static Long toLong(Object value) {
        String s = toStr(value);
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数格式错误，应为数字: " + s);
        }
    }

    static Integer toInt(Object value) {
        String s = toStr(value);
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数格式错误，应为整数: " + s);
        }
    }

    static LocalDate toDate(Object value) {
        String s = toStr(value);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("参数格式错误，日期应为 yyyy-MM-dd: " + s);
        }
    }
}
