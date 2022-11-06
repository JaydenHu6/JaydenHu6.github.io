---
layout: post
title: Debezium 订阅 Mysql binlog
date: 2022-11-05
last_modified_at: 2022-11-05
categories: [大数据]
toc:  true
original: true
author: [Jayden]
---

本文使用的各组件版本如下

`Debezium Version: 1.9.6` 

`Kafka Version: 3.3.1`

`MariaDB Server Version: 5.5.68`

## 开启 Binlog 

1. ##### 查看是否已经开启了 binlog

   ```
   show global variables like 'log_bin';
   ```

2. ##### 编辑 /etc/my.cnf，在 `[mysqld]`下添加如下配置

   ```shell
   # binlog 存储位置
   log_bin=/var/lib/mysql/bin-log
   # binlog 存储索引
   log_bin_index=/var/lib/mysql/mysql-bin.index
   # binlog 超时时间，单位为天
   expire_logs_days=7
   # 服务器 id ，如果是 Mysql 集群，则需要配置
   server_id=0002
   # binlog 格式，分为 3 种，分别是 statement、row、mixed
   # 参考 https://blog.csdn.net/mycwq/article/details/17136997
   binlog_format=ROW
   ```

## 准备订阅账号

​	生产环境对数据库权限需要严格控制，按照最小权限原则，新建一个只读账号，赋予 `Reload`、`Replication client`、`Replication slave`、`Select`、`Lock tables`权限即可，创建用户以及授权语句如下

```sql
-- 创建 debezium 用户
CREATE USER 'debezium'@'%' IDENTIFIED BY '密码';
SET PASSWORD FOR 'debezium'@'%' = PASSWORD('密码');
-- 重载权限，执行 flush xxxxx
GRANT Reload ON *.* TO 'debezium'@'%';
-- 显示 slave binlog，show slave status
GRANT Replication client ON *.* TO 'debezium'@'%';
-- 显示 master binlog，show slave status
GRANT Replication slave ON *.* TO 'debezium'@'%';
-- 授予 default 库 Select 权限
GRANT Select ON `default`.* TO 'debezium'@'%';
-- 授予 default 库 Lock tables 权限
GRANT Lock tables ON `default`.* TO 'debezium'@'%';
```

## 启动 Kafka Connector

1. 下载 debezium mysql connector 并解压，见[官网](https://debezium.io/releases/1.9/#installation)

2. 编辑 kafka 配置文件 `connect-distributed.properties`，配置 `plugin.path`，配置步骤1中 debezium-connector-mysql 文件夹绝对路径，如果已经配置了其他数据库(如 postgresql) 的 debezium connect，使用逗号分割

   ```properties
   # 以下为示例
   plugin.path=/root/develop/opt/kafka_2.12-3.3.1/plugins/debezium-connector-mysql
   ```

3. 如果 kafka connector 已经启动了，需要先关闭再重启，因为启动后在前台运行，生产环境放到后台运行即可，以下为启动命令

   ```shell
   nohup bin/connect-distributed.sh \
   		config/connect-distributed.properties \
   		2>&1 >> logs/destributed-connect.log &
   ```

4.  检查 kafka connector 是否已经加载了 debezium connector 插件

   ```shell
   curl -H "Accept:application/json" localhost:8083/connector-plugins/
   ```

​	

## 订阅 Binlog

​	订阅 binlog 只需要对 kafka connector 发送订阅请求即可，请求中需要包含以下内容

```JSON
{
    "name": "connector 名字",
    "config": {
      "connector.class": "io.debezium.connector.mysql.MySqlConnector",
      "database.hostname": "mysql host",
      "database.port": "mysql 端口",
      "database.user": "mysql 用户",
      "database.password": "mysql 用户名",
      "database.server.id": "mysql server id，表示订阅哪台机器的 binlog，我们使用 master 的 0002",
      "database.server.name": "记录 DDL 变动的 topic ",
      "database.include.list": "需要订阅的数据库，多个使用逗号分割",
      "database.history.kafka.bootstrap.servers": "kafka服务器地址",
      "database.history.kafka.topic": "记录所有 schema 变动记录的 kafka topic",
      "include.schema.changes": "true 是否捕捉 schema 变动",
      "include.query":"true 是否将执行的 sql 也记录下来"
    }
}
```

1. 将上述配置写入请求体文件中，以便保存

2. 发送订阅请求

   ```shell
   curl -X POST -H "Accept:application/json" \
   		-H "Content-Type:application/json" \
   		localhost:8083/connectors/ \
   		-d @请求体文件名
   ```

3. 验证 connector 是否已经连接，不出意外的话

   ```shell
   curl -H "Accept:application/json" localhost:8083/connectors/
   ```

## 问题

1. kafka connector 与 debezium 版本问题，刚开始我的 debezium 版本为 `2.0`，配置都没有问题，但是注册不了kafka connector 中，将 debezium 版本换为 `1.9` 即可解决，github 上也有人遇到了类似的[问题](https://github.com/DataReply/kafka-connect-mongodb/issues/23)

2. debezium mysql connector 解析有误，执行以下语句，识别不了数据库名称，[该问题已经提给了社区](https://issues.redhat.com/browse/DBZ-5802)

   ```
   alter table default.task add column xxxx varchar(200) comment 'cdc test';
   ```

## 排错思路

​	从源头开始排查，先检查 mysql 账号是否有效，接着通过 http 请求检查 debezium mysql connector 插件是否已经注册到 kafka connector 中，然后检查 connector 是否已经连接到 mysql，这一步通过 http 检查，如果有问题，查看 kafka connector 日志即可

