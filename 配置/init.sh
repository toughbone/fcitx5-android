#!/bin/bash

#
# SPDX-License-Identifier: LGPL-2.1-or-later
# SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
#

RIME_PATH="/storage/emulated/0/Android/data/org.fcitx.fcitx5.android/files/data/rime/"

echo "🚀 [1/3] 正在清理手机上的旧缓存 (build 目录)..."
adb shell rm -rf ${RIME_PATH}build

echo "📦 [2/3] 正在推送配置文件和词库..."
adb push toughbone.dict.yaml $RIME_PATH
adb push base_high.dict.yaml $RIME_PATH
adb push common_3500.dict.yaml $RIME_PATH
adb push t9_pinyin.schema.yaml $RIME_PATH
adb push luna_pinyin.extended.dict.yaml $RIME_PATH

echo "✅ [3/3] 推送完成！"
echo "请在手机键盘上点击 [重新部署 / Deploy] 以生效。"
