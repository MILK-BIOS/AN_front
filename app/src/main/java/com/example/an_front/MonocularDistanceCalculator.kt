package com.example.an_front

import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 简化版单目测距计算器
 * 不依赖OpenCV，但保留基本的距离估计功能
 */
class MonocularDistanceCalculator {
    // 典型物体尺寸数据库（单位：米）
    private val typicalDimensions = mapOf(
        "person" to mapOf("height" to 1.7, "width" to 0.5),
        "car" to mapOf("height" to 1.5, "width" to 1.8),
        "traffic light" to mapOf("height" to 0.3, "width" to 0.3),
        "bus" to mapOf("height" to 3.0, "width" to 2.5),
        "bicycle" to mapOf("height" to 1.0, "width" to 1.7),
        "truck" to mapOf("height" to 3.5, "width" to 2.5),
        "motorcycle" to mapOf("height" to 1.2, "width" to 1.8),
        "stairs" to mapOf("height" to 0.15, "width" to 1.2),
        "slope" to mapOf("height" to 0.3, "width" to 3.0),
        "zebra crossing" to mapOf("width" to 4.0),
        "chair" to mapOf("height" to 1.2, "width" to 0.6),
        "laptop" to mapOf("height" to 0.5, "width" to 0.35)
    )

    // 相机参数
    private val focalLength = 400.0 // 估计的像素焦距
    private val cameraHeight = 1.7 // 相机离地高度（米）
    private val tiltAngle = 0.0 // 相机俯仰角（弧度）

    /**
     * 计算物体距离
     * @param box 检测框
     * @param className 物体类别名称
     * @param imgWidth 图像宽度
     * @param imgHeight 图像高度
     * @return 估计距离（米），如果无法计算则返回null
     */
    fun calculateDistance(box: RectF, className: String, imgWidth: Int, imgHeight: Int): Double? {
        // 检查是否有该类别的尺寸数据
        if (!typicalDimensions.containsKey(className)) {
            return null
        }

        val dimensions = typicalDimensions[className]!!
        
        // 基于物体高度估计距离
        if (dimensions.containsKey("height")) {
            val realHeight = dimensions["height"]!!
            val pixelHeight = box.height()
            
            // 使用透视投影公式估计距离
            val heightDistance = (realHeight * focalLength) / pixelHeight
            
            // 考虑相机倾斜角度
            val adjustedDistance = heightDistance * cos(tiltAngle)
            
            // 四舍五入到一位小数
            return (adjustedDistance * 10).toInt() / 10.0
        } 
        // 如果没有高度信息，尝试使用宽度
        else if (dimensions.containsKey("width")) {
            val realWidth = dimensions["width"]!!
            val pixelWidth = box.width()
            
            // 使用透视投影公式估计距离
            val widthDistance = (realWidth * focalLength) / pixelWidth
            
            // 四舍五入到一位小数
            return (widthDistance * 10).toInt() / 10.0
        }
        
        // 如果没有可用的尺寸信息
        return null
    }
    
    /**
     * 调整焦距参数以匹配设备
     * @param widthInPixels 图像宽度
     * @param sensorWidth 传感器实际宽度（毫米）
     */
    fun calibrateCamera(widthInPixels: Int, sensorWidth: Double = 5.0) {
        // 估计焦距值 = 像素宽度 * (传感器焦距 / 传感器宽度)
        // 这里使用简化计算，假设传感器焦距约为5mm
    }
}