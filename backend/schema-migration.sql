/* ============================================================
 * gym-rebuild: New Tables Migration Script
 * Target DB: smart_gym (MySQL 8.0+)
 * Created: 2026-07-18
 * ============================================================ */

-- 1. system_config (for Admin system settings toggle)
CREATE TABLE IF NOT EXISTS `system_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(100) NOT NULL COMMENT 'Config key (e.g. diet_16_8_enabled, carbon_cycle_enabled)',
  `config_value` VARCHAR(255) NOT NULL DEFAULT 'false' COMMENT 'Config value (true/false or JSON)',
  `description` VARCHAR(500) DEFAULT NULL COMMENT 'Human-readable description',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='System configuration (toggle switches, feature flags)';

-- Default config rows
INSERT IGNORE INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('diet_16_8_enabled', 'false', '16+8 intermittent fasting diet tracking'),
('carbon_cycle_enabled', 'false', 'Carbon cycle diet tracking');

-- 2. assessment_record (fitness test result with score breakdown)
CREATE TABLE IF NOT EXISTS `assessment_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT NOT NULL COMMENT 'Member ID',
  `test_date` DATE NOT NULL COMMENT 'Test date',
  `weight_kg` DECIMAL(5,1) DEFAULT NULL COMMENT 'Body weight in kg',
  `body_fat_percent` DECIMAL(4,1) DEFAULT NULL COMMENT 'Body fat percentage',
  `muscle_mass_kg` DECIMAL(5,1) DEFAULT NULL COMMENT 'Muscle mass in kg',
  `bmi` DECIMAL(4,1) DEFAULT NULL COMMENT 'BMI = weight(kg) / (height(m))^2',
  `bmi_score` INT DEFAULT NULL COMMENT 'BMI score out of 50',
  `fat_score` INT DEFAULT NULL COMMENT 'Body fat score out of 50',
  `total_score` INT DEFAULT NULL COMMENT 'Total assessment score out of 100',
  `gender` VARCHAR(10) DEFAULT NULL COMMENT 'male/female for scoring standard',
  `height_cm` DECIMAL(5,1) DEFAULT NULL COMMENT 'Height in cm',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT 'Optional remarks',
  PRIMARY KEY (`id`),
  KEY `idx_member_id` (`member_id`),
  KEY `idx_test_date` (`test_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Fitness assessment records with scoring';

-- 3. assessment_report (AI-generated assessment report)
CREATE TABLE IF NOT EXISTS `assessment_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `member_id` BIGINT NOT NULL COMMENT 'Member ID',
  `record_id` BIGINT DEFAULT NULL COMMENT 'FK to assessment_record.id',
  `total_score` INT DEFAULT NULL COMMENT 'Total score',
  `grade` VARCHAR(20) DEFAULT NULL COMMENT 'Grade: excellent/good/pass/needs_improvement',
  `ai_suggestion` TEXT DEFAULT NULL COMMENT 'AI-generated training/diet suggestion',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_member_id` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI-generated fitness assessment reports';

-- 4. assessment_rule (BMI & body fat scoring standards)
CREATE TABLE IF NOT EXISTS `assessment_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `rule_key` VARCHAR(50) NOT NULL COMMENT 'bmi_range or fat_range',
  `gender` VARCHAR(10) NOT NULL COMMENT 'male/female/all',
  `min_value` DECIMAL(4,1) DEFAULT NULL COMMENT 'Range min (inclusive)',
  `max_value` DECIMAL(4,1) DEFAULT NULL COMMENT 'Range max (exclusive)',
  `score` INT NOT NULL COMMENT 'Score for this range',
  `description` VARCHAR(200) DEFAULT NULL COMMENT 'e.g. BMI 18.5-24.9 = Normal = 45 points',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Scoring rules for BMI and body fat assessment';

-- Default scoring rules
INSERT IGNORE INTO `assessment_rule` (`rule_key`, `gender`, `min_value`, `max_value`, `score`, `description`) VALUES
-- BMI scoring (both genders, out of 50)
('bmi_range', 'all', 0.0, 18.5, 20, 'Underweight - 20 points'),
('bmi_range', 'all', 18.5, 24.9, 45, 'Normal - 45 points'),
('bmi_range', 'all', 24.9, 29.9, 30, 'Overweight - 30 points'),
('bmi_range', 'all', 29.9, 100.0, 15, 'Obese - 15 points'),
-- Body fat scoring for males (out of 50)
('fat_range', 'male', 0.0, 6.0, 20, 'Essential fat - 20 points'),
('fat_range', 'male', 6.0, 15.0, 45, 'Athletic - 45 points'),
('fat_range', 'male', 15.0, 20.0, 40, 'Fitness - 40 points'),
('fat_range', 'male', 20.0, 25.0, 25, 'Average - 25 points'),
('fat_range', 'male', 25.0, 100.0, 15, 'Obese - 15 points'),
-- Body fat scoring for females (out of 50)
('fat_range', 'female', 0.0, 13.0, 20, 'Essential fat - 20 points'),
('fat_range', 'female', 13.0, 20.0, 45, 'Athletic - 45 points'),
('fat_range', 'female', 20.0, 25.0, 40, 'Fitness - 40 points'),
('fat_range', 'female', 25.0, 32.0, 25, 'Average - 25 points'),
('fat_range', 'female', 32.0, 100.0, 15, 'Obese - 15 points');

-- ============================================================
-- Phase 6.5: 准会员/体验课功能新增字段 (2026-07-18)
-- ============================================================

ALTER TABLE member ADD COLUMN IF NOT EXISTS experience_used BOOLEAN DEFAULT FALSE COMMENT '是否已使用体验课';

ALTER TABLE group_class ADD COLUMN IF NOT EXISTS allow_visitor BOOLEAN DEFAULT FALSE COMMENT '是否允许准会员预约';
-- ============================================================
-- Phase 7: 角色定义 + 位置打卡 (2026-07-18)
-- ============================================================

-- 将所有 expire_date IS NULL 的现有记录改为"访客"
UPDATE member SET level = '访客' WHERE expire_date IS NULL;


-- ============================================================
-- Phase 8: 教练自动创建会员账号 (2026-07-19)
-- ============================================================

-- 已有教练的 member 数据迁移（将教练关联的访客记录升级为普通会员）
UPDATE member m 
INNER JOIN trainer t ON m.phone = t.phone 
SET m.level = '普通会员', m.expire_date = '2099-12-31' 
WHERE m.level = '访客' OR m.expire_date IS NULL;

-- ============================================================
-- Phase 9: 积分系统 (2026-07-19)
-- ============================================================

-- 1. member 表增加积分字段
ALTER TABLE member ADD COLUMN IF NOT EXISTS points INT DEFAULT 0 COMMENT '当前积分';

-- 2. 积分变更历史
CREATE TABLE IF NOT EXISTS points_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  member_id BIGINT NOT NULL COMMENT '会员ID',
  points_change INT NOT NULL COMMENT '变更分值（正=增加，负=扣除）',
  change_type VARCHAR(50) NOT NULL COMMENT '变更类型: check_in/paid_class/free_class/redemption/admin_adjust',
  reference_id BIGINT DEFAULT NULL COMMENT '关联业务记录ID',
  remark VARCHAR(200) DEFAULT NULL COMMENT '备注',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_member_id (member_id),
  KEY idx_change_type (change_type),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分变更历史';

-- 3. 积分兑换记录
CREATE TABLE IF NOT EXISTS points_redemption (
  id BIGINT NOT NULL AUTO_INCREMENT,
  member_id BIGINT NOT NULL COMMENT '会员ID',
  points_spent INT NOT NULL COMMENT '消耗积分数',
  redemption_type VARCHAR(50) NOT NULL COMMENT '兑换类型: pt_session/coupon/physical_goods',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending/approved/rejected',
  admin_id BIGINT DEFAULT NULL COMMENT '审批管理员ID',
  admin_remark VARCHAR(200) DEFAULT NULL COMMENT '审批备注',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  processed_at DATETIME DEFAULT NULL COMMENT '审批时间',
  PRIMARY KEY (id),
  KEY idx_member_id (member_id),
  KEY idx_status (status),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换记录';