# 面部情绪可视化分析工具 | Emotion Recognition Tool

Android 平台实时面部情绪识别应用，基于 TensorFlow Lite 实现高精度 7 分类情绪检测。

## 📋 项目介绍
本项目结合 Android 相机开发与深度学习模型，实现对人脸面部表情的实时捕获、分析与识别。
可应用于智能客服、情绪感知、人机交互等场景。

## ✨ 功能特性
- 📸 **相机实时检测**：调用 CameraX 实现相机预览与帧捕获
- 😊 **7 分类情绪识别**：支持愤怒、恐惧、开心、悲伤、惊讶、中性、轻蔑
- 📊 **实时置信度**：可视化显示每种情绪的概率百分比
- 🖼️ **静态图片分析**：支持相册图片导入识别
- 🎨 **简洁 UI**：流畅的操作体验

## 🔧 技术栈
- **语言**：Kotlin
- **相机框架**：CameraX
- **推理引擎**：TensorFlow Lite
- **构建工具**：Gradle

## 🚀 快速开始
1. **克隆项目**：`git clone https://github.com/xujiawei234/emotion-recognition-tool.git`
2. **打开项目**：使用 Android Studio 打开
3. **运行应用**：连接真机或启动模拟器，点击 Run
