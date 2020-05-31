# Vly
Vly is a not perfect open-source alternative for hololy.  

With Vly, every VTuber or VUP can access real world. This project contains 3D mode and Live2D support, so if you only have live2D model, don't worry, you can also use this.  

Vly 是一个并不完美的 hololy 开源替代。  
使用 Vly，任何 VTuber 或 VUP 可以造访现实世界。这个项目包含 3D 模式和 Live2D 支持，所以如果您只有 Live2D 模型，不要担心，您依然可以使用它。

## Feature 功能
### Embedded Capture and Record 内置拍照和录制
Embedded Capture and Record is implemented. This feature will give you a clean picture and video. Picture captured and Video recorded by this feature never contain control window even if control window is showing on the screen. To use this feature, just click `Allow Record` button on control window and give it permission for writing files. If you enable `Allow Reocrd` feature, press your `volume up` button for capture picture, hold `volume down` button to start record, release to finish.  
You can enable `Record Audio` to record audio to video file at the same time.  

内置拍照和录制功能已经被实现。这个功能将给您一个干净整洁的照片和视频。即使控制窗口显示在屏幕上，使用这个功能拍摄的照片和视频永远不会包含它们。想要使用这个功能，仅需要在控制窗口上点击 `允许录制` 按钮，然后授权软件写入文件到设备。如果启动了 `允许录制` 功能，按下手机的 `音量上键` 进行拍照，按住 `音量下键` 开始录制，释放按键停止录制。  
您可以启动 `录制音频` 来同时录制周围的音频到视频中。  

### Live2D Fake AR Render Support 支持 Live2D 伪造 AR 渲染
Unlike other program, this project can render Live2D Avatar using Offscreen OpenGL render. Live2D Model will always face to your camera, beacuse it doesn't have thinkness.  
与其他软件不同，这个项目可以使用 OpenGL 离屏渲染来渲染 Live2D 人物。由于 Live2D 模型没有厚度，所以它将永远面向摄像机。  

### Live2D Fake Shadow 伪造 Live2D 阴影
Beacuse Live2D is 2D picture transform, so it doesn't support 3D light and normal, so this project will render a fake shadow below your model to make your model assimilate into environments.  
由于 Live2D 为二维图像变换，所以其不支持三维光照和法线，所以这个项目在您的模型下方渲染了一个虚假的影子，使您的模型融入周围的环境中。

## Support Platfrom 支持的平台
Any android phone which can run ARCore.  
任何可以运行 ARCore 的 Android 手机。  

## Known Issue 已知问题
 * Imported GLB can not render material and texture (Temporarily use Internal model to solve this) 导入的 GLB 文件无法渲染材质和贴图（暂时使用内置模型来解决这个问题）

## Build 构建
First, you need download Live2D SDK and decompress to project root(`Framework` and `Core` directory is needed).  
Then you can build it as normal.  
This repo also contains CI support, you can fork this repo and enable CI for this to auto build Vly.

首先您需要下载 Live2D SDK 并将其解压缩到项目根目录（需要 `Framework` 和 `Core` 目录）。  
然后您就可以像其他软件一样编译它。  
这个仓库同时具有 CI 支持，您可以 fork 这个仓库，然后启动 CI 来自动编译 Vly。

## License 协议
This project is licensed under MIT Open Source License, expect `Framework` `Core` `OpenGL` and `app\src\main\cpp` directory.  
这个项目使用 MIT 开源协议授权，除了 `Framework` `Core` `OpenGL` 和 `app\src\main\cpp` 目录。

## License Issue (Why not Prebuild APK) 协议问题（为何没有预编译 APK）
According to Live2D License, this program is an "「拡張性アプリケーション」" program. If I want to provide prebuild program, aka publish this program, I must sign a contract with Live2D Company, and pay money for this. So I can not directly provide apk file, but I can provide auto build script using CI system.  
You only need is fork this repo and active CI for this repo. Wait for CI to build with you.  
These CI system is supported by project:  
 * Github Action
 * TravisCI

Why this can bypass license issue?   
Beacuse, if you fork this repo, you legally own these code, MIT open-source license give you this power. According to Live2D Lisense, you can make program to your self, so CI build these code (you own these code by clicking `fork` button) only for you.  
Remember, if you give your apk file to other, you need contact Live2D Company to sign contact for this and pay for it.

根据 Live2D 协议，这个项目是一个 “「拡張性アプリケーション」” 软件。如果我想要提供预编译的程序，也就是发布此应用，我必须与 Live2D 公司签署合同并付费。所以我不能直接提供 apk 文件，但我可以提供为持续集成系统使用的自动编译脚本。  
您只需要 fork 这个仓库，然后激活持续集成即可。等待持续集成系统为您编译。  
这些持续集成系统受到此项目支持：  
 * Github Action
 * TravisCI

为什么这可以绕过协议问题？  
因为如果您 fork 这个仓库，您在法律上拥有了这些代码，MIT 开源协议提供给您了这个权力。根据 Live2D 协议，您可以为您自己制作程序，所以持续集成仅为您启动了编译工作。（您在点击 `fork` 按钮时拥有了这些代码）  
记住，如果您将您的 apk 文件交给其他人，您需要联系 Live2D 公司并签署对应合同。  

## Contribute 贡献
This program is far from perfect, now I only give essential usage for Vly. If you can make this better, please feel free to pull request, or create your fork branch.  
Open source make our world better and better.   

这个程序距离完整还有很多路要走，现在我仅仅实现了 Vly 的基础功能。如果您能让它变得更好，欢迎您对此进行修改并提出合并请求或者创建您自己的分支。  
开源让我们的世界越来越美好。