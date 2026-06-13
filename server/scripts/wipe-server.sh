#!/usr/bin/env bash
# 清空 ECS 上的旧 Docker 服务（保留 /opt/timedrecorder 项目目录）
set -euo pipefail

echo "==> 停止并删除所有 Docker 容器/镜像/卷"
if command -v docker >/dev/null 2>&1; then
  docker stop $(docker ps -aq) 2>/dev/null || true
  docker rm $(docker ps -aq) 2>/dev/null || true
  docker system prune -a --volumes -f || true
fi

echo "==> 停止宿主机 Nginx（若存在）"
systemctl stop nginx 2>/dev/null || true
systemctl disable nginx 2>/dev/null || true

echo "==> 清理其他旧项目目录（保留 /opt/timedrecorder）"
find /opt -mindepth 1 -maxdepth 1 ! -name 'timedrecorder' -exec rm -rf {} + 2>/dev/null || true
rm -rf /srv/* 2>/dev/null || true

echo "==> 当前资源状态"
free -h
df -h
docker ps -a 2>/dev/null || echo "docker not installed"

echo "==> 清空完成"
