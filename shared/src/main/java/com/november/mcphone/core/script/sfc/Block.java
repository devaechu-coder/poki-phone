package com.november.mcphone.core.script.sfc;

/**
 * 一个顶层块。{@code startLine} 是块内容第一行在原文件里的行号（1-based）：块内第 k 行 = 原文件第 startLine + k - 1 行，
 * 错误行号映射全靠它（§9.8）。内容按行拼回，每行以 \n 结尾，行尾的 \r 已去掉。
 */
public record Block(String name, int startLine, String content) {
}
