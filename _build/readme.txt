debian9上でコンパイルした。

参照: https://wiki.videolan.org/AndroidCompile/
上記以外のTips


msgfmtが必要:
 apt-get install gettext


特定のバージョンをcloneする:
git clone -b 3.1.1 --depth 1 https://code.videolan.org/videolan/vlc-android.git


ndk-r18b:
https://github.com/android-ndk/ndk/wiki


AndroidSDKのライセンス承諾を一括で行う方法:
sdkmanager --licenses



./compile.sh -l --release -a all

libvlc-all-3.1.x.aarが作成される。
