import cv2
import torch
import numpy as np
from ultralytics import YOLO

class EnhancedMonocularMeasurer:
    def __init__(self, model_path='yolov10l.pt'):
        # 初始化YOLOv10模型（修正加载方式）
        self.model = YOLO(model_path)
        
        # 增强的物体尺寸数据库（单位：米）[1](@ref)
        self.typical_dimensions = {
            'person': {'height': 1.7, 'ground_width': 0.5},
            'car': {'height': 1.5, 'ground_width': 1.8},
            'traffic light': {'height': 0.3, 'ground_width': 0.3},
            'bus stop': {'height': 2.5, 'ground_width': 1.2},
            'bicycle': {'height': 1.0, 'ground_width': 1.7},
            'stairs': {'height': 0.15, 'ground_width': 1.2},
            'slope': {'height': 0.3, 'ground_width': 3.0},
            'zebra crossing': {'ground_width': 4.0}  # 特殊处理
        }

        # 相机标定参数（需实际标定）[3](@ref)
        self.camera_matrix = np.array([
            [5882, 0, 2794],   # 焦距fx, 0, 中心点cx
            [0, 5845, 2212],   # 0, 焦距fy, 中心点cy 
            [0, 0, 1]
        ])
        self.dist_coeffs = np.array([0.1, -2.2, 0, -0.01])  # 畸变系数k1,k2,p1,p2  
        
        # 地面假设参数（相机安装高度）
        self.camera_height = 1.5  # 相机离地高度（米）
        self.tilt_angle = np.deg2rad(0)  # 相机俯仰角

    def _ground_projection(self, box_points, img_shape):
        """地面投影计算（改进几何模型）[3](@ref)"""
        h, w = img_shape[:2]
        
        # 畸变校正
        pts_undistorted = cv2.undistortPoints(
            box_points.astype(np.float32),
            self.camera_matrix,
            self.dist_coeffs,
            P=self.camera_matrix
        )
        
        # 构建投影矩阵
        rot_mat = np.array([
            [1, 0, 0],
            [0, np.cos(self.tilt_angle), -np.sin(self.tilt_angle)],
            [0, np.sin(self.tilt_angle), np.cos(self.tilt_angle)]
        ])
        
        # 地面平面方程（z=0）
        ground_normal = np.array([0, 1, 0])
        ground_point = np.array([0, -self.camera_height, 0])
        
        # 射线投影计算
        rays = cv2.convertPointsToHomogeneous(pts_undistorted)
        rays = rays @ rot_mat.T
        
        # 计算交点
        denom = rays @ ground_normal
        t = ((ground_point - np.array([0,0,0])) @ ground_normal) / denom
        intersection = rays * t.reshape(-1,1)
        
        return intersection[:,0,:2]

    def _calculate_distance(self, box, class_name, img_shape):
        """改进的距离计算（融合高度和地面投影）[1,3](@ref)"""
        if class_name not in self.typical_dimensions:
            return None

        # 获取物体底部中心点坐标
        x1, y1, x2, y2 = box
        bottom_center = np.array([[(x1+x2)/2, y2]], dtype=np.float32)
        
        # 地面投影
        projected = self._ground_projection(bottom_center, img_shape)[0]
        horizontal_distance = np.linalg.norm(projected)
        
        # 高度验证（当物体有高度时）
        if 'height' in self.typical_dimensions[class_name]:
            real_height = self.typical_dimensions[class_name]['height']
            pixel_height = y2 - y1
            fy = self.camera_matrix[1,1]
            height_distance = (real_height * fy) / (pixel_height * np.cos(self.tilt_angle))
            
            # 融合两种距离估计
            final_distance = (horizontal_distance + height_distance) / 2
        else:
            final_distance = horizontal_distance

        return round(final_distance, 1)

    def visualize_results(self, image, results):
        """增强的可视化方法"""
        img = image.copy()
        font = cv2.FONT_HERSHEY_SIMPLEX
        font_scale = 0.7
        thickness = 2

        # 解析检测结果
        for box in results.boxes:
            xyxy = box.xyxy.cpu().numpy()[0]
            conf = box.conf.cpu().numpy()[0]
            cls = int(box.cls.cpu().numpy()[0])
            class_name = self.model.names[cls]

            x1, y1, x2, y2 = map(int, xyxy)
            distance = self._calculate_distance(xyxy, class_name, image.shape)
            
            if distance is None:
                continue

            # 动态颜色和尺寸
            color = (0, 255, 0) if distance < 20 else (0, 0, 255)
            box_thickness = 2 if distance < 50 else 3
            text_size = 0.7 if distance < 50 else 0.6

            # 绘制边界框
            cv2.rectangle(img, (x1, y1), (x2, y2), color, box_thickness)
            
            # 绘制信息板
            label = f"{class_name} {distance}m"
            (text_width, text_height), _ = cv2.getTextSize(label, font, text_size, thickness)
            cv2.rectangle(img, (x1, y1-text_height-10), (x1+text_width, y1), color, -1)
            cv2.putText(img, label, (x1, y1-10), font, text_size, (255,255,255), thickness)
            
            print(f"检测到 {class_name}: {distance}m (置信度: {conf:.2f})")

        return img

    def process_image(self, image_path):
        """完整的处理流程"""
        # 读取并预处理图像
        img = cv2.imread(image_path)
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        undistorted_img = cv2.undistort(img, self.camera_matrix, self.dist_coeffs)
        
        # 执行推理
        results = self.model(undistorted_img, imgsz=640, conf=0.4)[0]
        
        # 结果可视化
        visualized_img = self.visualize_results(undistorted_img, results)
        
        # 保存并显示结果
        output_path = image_path.replace('.', '_result.')
        cv2.imwrite(output_path, cv2.cvtColor(visualized_img, cv2.COLOR_RGB2BGR))
        
        return visualized_img

if __name__ == "__main__":
    detector = EnhancedMonocularMeasurer()
    result = detector.process_image("/private/workspace/fhs/AN/test.jpg")