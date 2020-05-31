/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "LAppLive2DManager.hpp"
#include <string>
#include <GLES2/gl2.h>
#include <Rendering/CubismRenderer.hpp>
#include "LAppPal.hpp"
#include "LAppDefine.hpp"
#include "LAppDelegate.hpp"
#include "LAppModel.hpp"
#include "LAppView.hpp"

using namespace Csm;
using namespace LAppDefine;
using namespace std;

namespace {
    LAppLive2DManager *s_instance = NULL;

    void FinishedMotion(ACubismMotion *self) {
        LAppPal::PrintLog("Motion Finished: %x", self);
    }
}

LAppLive2DManager *LAppLive2DManager::GetInstance() {
    if (s_instance == NULL) {
        s_instance = new LAppLive2DManager();
    }

    return s_instance;
}

void LAppLive2DManager::ReleaseInstance() {
    if (s_instance != NULL) {
        delete s_instance;
    }

    s_instance = NULL;
}

LAppLive2DManager::LAppLive2DManager()
        : _viewMatrix(NULL) {
    ChangeScene();
}

LAppLive2DManager::~LAppLive2DManager() {
    ReleaseAllModel();
}

void LAppLive2DManager::ReleaseAllModel() {
    delete _models;
}

LAppModel *LAppLive2DManager::GetModel() const {
    return _models;
}
//
//void LAppLive2DManager::OnDrag(csmFloat32 x, csmFloat32 y) const
//{
//    for (csmUint32 i = 0; i < _models.GetSize(); i++)
//    {
//        LAppModel* model = GetModel(i);
//
//        model->SetDragging(x, y);
//    }
//}
//
//void LAppLive2DManager::OnTap(csmFloat32 x, csmFloat32 y)
//{
//    if (DebugLogEnable)
//    {
//        LAppPal::PrintLog("[APP]tap point: {x:%.2f y:%.2f}", x, y);
//    }
//
//    for (csmUint32 i = 0; i < _models.GetSize(); i++)
//    {
//        if (_models[i]->HitTest(HitAreaNameHead, x, y))
//        {
//            if (DebugLogEnable)
//            {
//                LAppPal::PrintLog("[APP]hit area: [%s]", HitAreaNameHead);
//            }
//            _models[i]->SetRandomExpression();
//        }
//        else if (_models[i]->HitTest(HitAreaNameBody, x, y))
//        {
//            if (DebugLogEnable)
//            {
//                LAppPal::PrintLog("[APP]hit area: [%s]", HitAreaNameBody);
//            }
//            _models[i]->StartRandomMotion(MotionGroupTapBody, PriorityNormal, FinishedMotion);
//        }
//    }
//}

void LAppLive2DManager::OnUpdate() const {
    CubismMatrix44 projection;
    int width = LAppDelegate::GetInstance()->GetWindowWidth();
    int height = LAppDelegate::GetInstance()->GetWindowHeight();
    projection.Scale(1.0f, static_cast<float>(width) / static_cast<float>(height));

    if (_viewMatrix != NULL) {
        projection.MultiplyByMatrix(_viewMatrix);
    }

    const CubismMatrix44 saveProjection = projection;
    LAppModel *model = GetModel();
    projection = saveProjection;

    // モデル1体描画前コール
    LAppDelegate::GetInstance()->GetView()->PreModelDraw(*model);

    model->Update();
    model->Draw(projection);///< 参照渡しなのでprojectionは変質する

    // モデル1体描画後コール
    LAppDelegate::GetInstance()->GetView()->PostModelDraw(*model);
}

//void LAppLive2DManager::NextScene()
//{
//    csmInt32 no = (_sceneIndex + 1) % ModelDirSize;
//    ChangeScene(no);
//}

void LAppLive2DManager::ChangeScene() {
    if (DebugLogEnable) {
        LAppPal::PrintLog("[APP]model index: %d", 0);
    }

    // ModelDir[]に保持したディレクトリ名から
    // model3.jsonのパスを決定する.
    // ディレクトリ名とmodel3.jsonの名前を一致させておくこと.
    std::string modelPath = "";
    std::string modelJsonName = LAppDefine::ModelName;
    modelJsonName += ".model3.json";

    //ReleaseAllModel();
    _models = new LAppModel();
    _models->LoadAssets(modelPath.c_str(), modelJsonName.c_str());

    /*
     * モデル半透明表示を行うサンプルを提示する。
     * ここでUSE_RENDER_TARGET、USE_MODEL_RENDER_TARGETが定義されている場合
     * 別のレンダリングターゲットにモデルを描画し、描画結果をテクスチャとして別のスプライトに張り付ける。
     */
    {
#if defined(USE_RENDER_TARGET)
        // LAppViewの持つターゲットに描画を行う場合、こちらを選択
        LAppView::SelectTarget useRenderTarget = LAppView::SelectTarget_ViewFrameBuffer;
#elif defined(USE_MODEL_RENDER_TARGET)
        // 各LAppModelの持つターゲットに描画を行う場合、こちらを選択
        LAppView::SelectTarget useRenderTarget = LAppView::SelectTarget_ModelFrameBuffer;
#else
        // デフォルトのメインフレームバッファへレンダリングする(通常)
        LAppView::SelectTarget useRenderTarget = LAppView::SelectTarget_None;
#endif

#if defined(USE_RENDER_TARGET) || defined(USE_MODEL_RENDER_TARGET)
        // モデル個別にαを付けるサンプルとして、もう1体モデルを作成し、少し位置をずらす
        _models = new LAppModel();
        _models->LoadAssets(modelPath.c_str(), modelJsonName.c_str());
        _models->GetModelMatrix()->TranslateX(0.2f);
#endif

        LAppDelegate::GetInstance()->GetView()->SwitchRenderingTarget(useRenderTarget);

        // 別レンダリング先を選択した際の背景クリア色
        float clearColor[3] = {1.0f, 1.0f, 1.0f};
        LAppDelegate::GetInstance()->GetView()->SetRenderTargetClearColor(clearColor[0],
                                                                          clearColor[1],
                                                                          clearColor[2]);
    }
}
