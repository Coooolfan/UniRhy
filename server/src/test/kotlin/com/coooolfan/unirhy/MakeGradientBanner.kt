package com.coooolfan.unirhy

import org.junit.jupiter.api.Test
import java.awt.Color
import java.io.FileWriter
import kotlin.math.max
import kotlin.math.min


class MakeGradientBanner {
    @Test
    fun main() {
        // ================= 配置区 =================

        // 1. 定义 ASCII 艺术字 (UniRhy + 律动条)
        val ART_LINES = arrayOf(
            "  _   _       _ ____  _           ",
            " | | | |_ __ (_)  _ \\| |__  _   _ ",
            " | | | | '_ \\| | |_) | '_ \\| | | |",
            " | |_| | | | | |  _ <| | | | |_| |",
            "  \\___/|_| |_|_|_| \\_\\_| |_|\\__, |",
            "                            |___/ ",
            "",
            "  ▂ ▃ ▅ ▆ ▇ █ ▇ ▆ ▅ ▃ ▂   ▂ ▃ ▅ ▆ ▇",  // 律动条
            "  UniRhy Music Server :: Loading..."
        )


        // 2. 定义渐变色 (RGB)
        // 起始色：深橙红色 (热烈)
        val START_COLOR = Color(255, 69, 0)


        // 结束色：金黄色 (明亮)
        val END_COLOR = Color(255, 215, 0)


        // 3. 渐变模式
        // true = 左上到右下 (对角线渐变)
        // false = 从左到右 (水平渐变)
        val DIAGONAL_MODE = false


        // ================= 生成逻辑 =================
        val buffer = StringBuilder()
        val height = ART_LINES.size
        var maxWidth = 0
        for (line in ART_LINES) maxWidth = max(maxWidth, line.length)

        for (y in 0..<height) {
            val line = ART_LINES[y]
            for (x in 0..<line.length) {
                val c = line[x]


                // 跳过空格，减少文件体积，也不影响显示
                if (c == ' ') {
                    buffer.append(" ")
                    continue
                }

                // 计算渐变进度 (0.0 到 1.0)
                var ratio: Float = if (DIAGONAL_MODE) {
                    // 对角线：x 和 y 共同影响颜色
                    (x + y * 2).toFloat() / (maxWidth + height * 2)
                } else {
                    // 水平：只由 x 影响
                    x.toFloat() / maxWidth
                }


                // 限制范围在 0-1 之间
                ratio = min(1.0f, max(0.0f, ratio))

                // 颜色插值计算 (Linear Interpolation)
                val r = (START_COLOR.red + ratio * (END_COLOR.red - START_COLOR.red)).toInt()
                val g = (START_COLOR.green + ratio * (END_COLOR.green - START_COLOR.green)).toInt()
                val b = (START_COLOR.blue + ratio * (END_COLOR.blue - START_COLOR.blue)).toInt()

                // 生成 ANSI TrueColor 转义码: \033[38;2;R;G;Bm
                buffer.append(String.format("\u001b[38;2;%d;%d;%dm%c", r, g, b, c))
            }
            // 换行前重置颜色，防止背景色污染
            buffer.append("\u001b[0m\n")
        }


        // 添加版本号后缀 (使用Spring变量)
        buffer.append("\u001b[38;2;100;100;100m") // 灰色
        buffer.append($$" :: Powered by Spring Boot ${spring-boot.version} ::")
        buffer.append("\u001b[0m")


        FileWriter("src/main/resources/banner.txt").use { writer ->
            writer.write(buffer.toString())
            println("🎉 渐变 Banner 已生成!")
            println("📂 路径: src/main/resources/banner.txt")
        }
    }

}
