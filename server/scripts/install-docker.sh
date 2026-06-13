#!/usr/bin/env bash
# 在 Alibaba Cloud Linux 3 安装 Docker CE 与 Compose 插件
set -euo pipefail

echo "==> 安装 Docker CE"
dnf install -y dnf-utils
dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

echo "==> 启动 Docker"
systemctl enable --now docker

echo "==> 验证版本"
docker --version
docker compose version

echo "==> Docker 安装完成"
