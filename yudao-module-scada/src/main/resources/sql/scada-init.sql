-- =====================================================================
-- SCADA 模块 MySQL DDL
-- 5 张业务表：device/point/task/rule/event
-- 时序数据不入 MySQL，走 TDengine（见 framework/scada/tdengine/TDengineSqlMapper.initSchema）
-- 所有表带 tenant_id/creator/create_time/updater/update_time/deleted 标准字段
-- =====================================================================

-- SCADA 设备表
CREATE TABLE IF NOT EXISTS `scada_device` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code` VARCHAR(64) NOT NULL COMMENT '设备编码，租户内唯一',
    `name` VARCHAR(128) NOT NULL COMMENT '设备名称',
    `protocol_type` TINYINT UNSIGNED NOT NULL COMMENT '协议类型 10=ModbusTCP/11=ModbusRTU/20=OPCUA/30=S7-1200/31=S7-1500/40=MQTTSparkplug',
    `conn_params_json` TEXT NOT NULL COMMENT '连接参数 JSON',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '运行状态 0=禁用/1=未知/2=连接中/3=已连接/4=重连中/5=错误',
    `last_heartbeat_at` DATETIME NULL COMMENT '最近心跳时间',
    `last_error` VARCHAR(512) NULL COMMENT '最近错误信息',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `remark` VARCHAR(512) NULL COMMENT '备注',

    -- 芋道标准字段
    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户 ID',
    `creator` VARCHAR(64) NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` VARCHAR(64) NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否删除',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`, `deleted`),
    KEY `idx_protocol_enabled` (`protocol_type`, `enabled`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCADA 设备';

-- SCADA 点位表
CREATE TABLE IF NOT EXISTS `scada_point` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `device_id` BIGINT NOT NULL COMMENT '所属设备 ID',
    `tag` VARCHAR(64) NOT NULL COMMENT '点位标签，租户内唯一，用作 TDengine 子表名',
    `name` VARCHAR(128) NOT NULL COMMENT '点位名称',
    `address` VARCHAR(128) NOT NULL COMMENT '协议地址',
    `point_type` TINYINT UNSIGNED NOT NULL COMMENT '点位类型 1=BOOL/2=INT/3=FLOAT/4=STR',
    `eng_unit` VARCHAR(32) NULL COMMENT '工程单位 ℃/MPa/rpm',
    `scale` DECIMAL(18,6) NOT NULL DEFAULT 1 COMMENT '工程量缩放系数',
    `offset` DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '工程量零点偏移',
    `min_val` DECIMAL(18,6) NULL COMMENT '量程下限',
    `max_val` DECIMAL(18,6) NULL COMMENT '量程上限',
    `dead_band` DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '死区百分比 0-100',
    `scan_rate_ms` INT NOT NULL DEFAULT 1000 COMMENT '扫描周期毫秒',
    `rw_flag` CHAR(2) NOT NULL DEFAULT 'R' COMMENT '读写标志 R/W/RW',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `remark` VARCHAR(512) NULL COMMENT '备注',

    `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户 ID',
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_tag` (`tenant_id`, `tag`, `deleted`),
    KEY `idx_device` (`device_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCADA 点位';

-- SCADA 采集任务表
CREATE TABLE IF NOT EXISTS `scada_collect_task` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code` VARCHAR(64) NOT NULL COMMENT '任务编码',
    `name` VARCHAR(128) NOT NULL COMMENT '任务名称',
    `device_id` BIGINT NOT NULL COMMENT '关联设备 ID',
    `task_type` VARCHAR(16) NOT NULL DEFAULT 'POLL' COMMENT '任务类型 POLL/SUBSCRIBE',
    `interval_ms` INT NOT NULL DEFAULT 1000 COMMENT '采集周期毫秒',
    `point_ids_json` TEXT NOT NULL COMMENT '关联点位 ID JSON [101,102,103]',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `concurrent` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否允许并发执行',
    `last_run_at` DATETIME NULL COMMENT '上次执行时间',
    `last_duration_ms` BIGINT NULL COMMENT '上次执行耗时毫秒',
    `last_error` VARCHAR(512) NULL COMMENT '上次错误信息',
    `run_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计执行次数',
    `error_count` BIGINT NOT NULL DEFAULT 0 COMMENT '累计错误次数',

    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`, `deleted`),
    KEY `idx_enabled_due` (`enabled`, `last_run_at`),
    KEY `idx_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCADA 采集任务';

-- SCADA 告警规则表
CREATE TABLE IF NOT EXISTS `scada_alarm_rule` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code` VARCHAR(64) NOT NULL COMMENT '规则编码',
    `name` VARCHAR(128) NOT NULL COMMENT '规则名称',
    `point_id` BIGINT NOT NULL COMMENT '关联点位 ID',
    `rule_type` TINYINT UNSIGNED NOT NULL COMMENT '规则类型 1=THRESHOLD/2=BAND/3=RATE',
    `high_limit` DECIMAL(18,6) NULL COMMENT '阈值上限',
    `low_limit` DECIMAL(18,6) NULL COMMENT '阈值下限',
    `rate_limit` DECIMAL(18,6) NULL COMMENT '变化率阈值 value/sec',
    `on_delay_ms` INT NOT NULL DEFAULT 2000 COMMENT '触发延迟毫秒',
    `off_delay_ms` INT NOT NULL DEFAULT 5000 COMMENT '复归延迟毫秒',
    `dead_band` DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '死区百分比',
    `severity` TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '严重度 1=LOW/2=MEDIUM/3=HIGH/4=CRITICAL',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `notify_template` VARCHAR(512) NULL COMMENT '通知模板',
    `remark` VARCHAR(512) NULL,

    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`, `deleted`),
    KEY `idx_point_enabled` (`point_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCADA 告警规则';

-- SCADA 告警事件表
CREATE TABLE IF NOT EXISTS `scada_alarm_event` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rule_id` BIGINT NOT NULL COMMENT '规则 ID',
    `point_id` BIGINT NOT NULL COMMENT '点位 ID',
    `device_id` BIGINT NOT NULL COMMENT '设备 ID（冗余，便于按设备查询）',
    `triggered_at` DATETIME NOT NULL COMMENT '触发时间',
    `cleared_at` DATETIME NULL COMMENT '复归时间',
    `acked_at` DATETIME NULL COMMENT '确认时间',
    `acked_by` VARCHAR(64) NULL COMMENT '确认人',
    `trigger_value` DECIMAL(18,6) NULL COMMENT '触发时的值',
    `peak_value` DECIMAL(18,6) NULL COMMENT '峰值',
    `state` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态 0=正常/1=未确认/2=已确认/3=复归未确认/9=已屏蔽',
    `severity` TINYINT UNSIGNED NOT NULL COMMENT '严重度（冗余自规则）',
    `message` VARCHAR(512) NULL COMMENT '触发消息',
    `notified` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已通知',

    `tenant_id` BIGINT NOT NULL DEFAULT 0,
    `creator` VARCHAR(64) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updater` VARCHAR(64) NULL,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),
    KEY `idx_device_state` (`device_id`, `state`),
    KEY `idx_rule` (`rule_id`),
    KEY `idx_state_severity` (`state`, `severity`),
    KEY `idx_triggered_at` (`triggered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SCADA 告警事件';
