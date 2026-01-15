#!/usr/bin/bash


YUBIKEY_DEVICE=`lsusb | grep Yubikey | awk -F'[ :]' '{print "/dev/bus/usb/"$2"/"$4}'`

echo $YUBIKEY_DEVICE

  #-v /run/pcscd/pcscd.comm:/run/pcscd/pcscd.comm \
podman run --rm -it \
  --device $YUBIKEY_DEVICE:$YUBIKEY_DEVICE \
  -v ~/mypubkey:/root/mypubkey.pub:Z \
  -v ~/code/rust-android-gradle:/build:Z \
  -v ~/.gradle/gradle.properties:/root/.gradle/gradle.properties \
  ghcr.io/mullvad/mullvadvpn-app-build-android:6f6ff93a54 bash -c "
apt update
apt install -y scdaemon pcscd
service pcscd start
gpg --import /root/mypubkey.pub
echo -e 'trust\n5\ny\nsave' | gpg --command-fd 0 --edit-key E7271BD9973A5FA8
gpg --card-status
bash
# run the following manually
# export GPG_TTY=$(tty)
# ./gradlew publishPlugins --validate-only
"

