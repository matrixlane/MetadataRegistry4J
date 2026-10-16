<template>
  <div class="item-registration-state-section">
    <!-- Registration_State 属性（ISO/IEC 11179-3:2023 表 22）：留空不变更，创建落默认（已记录/当前时间）；
         各字段说明为 ISO/IEC 11179-3 属性定义原文，经标签旁图标悬浮展示 -->
    <el-form-item>
      <template #label>
        <span class="field-label">
          <span>注册状态</span>
          <el-tooltip :content="t('itemRegistrationState.registrationStatusPlaceholder')" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <template v-if="isEditing">
        <div class="readonly-registration-status" data-test="readonly-registration-status">
          <el-tag size="small" data-test="readonly-status-tag">
            {{ model.registrationStatus ? t('registrationStatus.' + model.registrationStatus) : '-' }}
          </el-tag>
        </div>
      </template>
      <!-- 新建模式的初始状态白名单与后端 ALLOWED_INITIAL_STATUSES 对齐：仅 INCOMPLETE（不完整）、
           APPLICATION（应用）、HISTORICAL（历史）；生命周期态须经推进流程获得 -->
      <el-select v-else v-model="model.registrationStatus" style="width: 100%;" clearable>
        <el-option
          v-for="s in INITIAL_REGISTRATION_STATUSES"
          :key="s"
          :label="t(`registrationStatus.${s}`)"
          :value="s"
        />
      </el-select>
    </el-form-item>
    <el-form-item>
      <template #label>
        <span class="field-label">
          <span>管理状态</span>
          <el-tooltip :content="t('itemRegistrationState.administrativeStatusPlaceholder')" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-input v-model="model.administrativeStatus" clearable />
    </el-form-item>
    <el-form-item>
      <template #label>
        <span class="field-label">
          <span>生效日期</span>
          <el-tooltip :content="t('itemRegistrationState.effectiveDatePlaceholder')" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-date-picker
        v-model="model.effectiveDate"
        type="datetime"
        placeholder="请选择"
        style="width: 100%;"
        value-format="YYYY-MM-DDTHH:mm:ss"
        clearable
      />
    </el-form-item>
    <el-form-item>
      <template #label>
        <span class="field-label">
          <span>截止日期</span>
          <el-tooltip :content="t('itemRegistrationState.untilDatePlaceholder')" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-date-picker
        v-model="model.untilDate"
        type="datetime"
        placeholder="请选择"
        style="width: 100%;"
        value-format="YYYY-MM-DDTHH:mm:ss"
        clearable
      />
    </el-form-item>
    <el-form-item>
      <template #label>
        <span class="field-label">
          <span>管理备注</span>
          <el-tooltip :content="t('itemRegistrationState.administrativeNotePlaceholder')" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-input v-model="model.administrativeNote" type="textarea" :rows="2" />
    </el-form-item>
    <el-form-item>
      <template #label>
        <span class="field-label">
          <span>未决问题</span>
          <el-tooltip :content="t('itemRegistrationState.unresolvedIssuePlaceholder')" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-input v-model="model.unresolvedIssue" type="textarea" :rows="2" />
    </el-form-item>
    <el-form-item>
      <template #label>
        <span class="field-label">
          <span>{{ t('assistant.explanatoryComment') }}</span>
          <el-tooltip :content="t('itemRegistrationState.explanatoryCommentPlaceholder')" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </span>
      </template>
      <el-input
        v-model="model.explanatoryComment"
        type="textarea"
        :rows="3"
        data-test="explanatory-comment"
      />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';
import { InfoFilled } from '@element-plus/icons-vue';

/** 新建条目的可选初始注册状态：仅 INCOMPLETE 与文档态入口 APPLICATION/HISTORICAL
 *  （与后端 ItemAdministrationService.ALLOWED_INITIAL_STATUSES 白名单一致）。 */
const INITIAL_REGISTRATION_STATUSES = ['INCOMPLETE', 'HISTORICAL', 'APPLICATION'] as const;

const { t } = useI18n();

interface Props {
  /** 编辑模式（宿主 ItemFormDialog 据 form.id 判定）：注册状态只读展示；新建模式保持可选初始状态 */
  isEditing?: boolean;
}

withDefaults(defineProps<Props>(), {
  isEditing: false,
});

/** 与宿主表单共享的 registration 对象（同 ItemRegistrationSection 的 RegistrationModel），
 *  状态字段由本组件编辑，提交负载仍由 ItemRegistrationSection.buildPayload 统一组装。 */
const model = defineModel<Record<string, any>>({ required: true });
</script>

<style scoped lang="scss">
/* 标签与解释性图标底部对齐：图标位于标题文字右下角 */
.field-label {
  display: inline-flex;
  align-items: flex-end;
  gap: 2px;
  line-height: 1;
}

.info-icon {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  cursor: help;
  transition: color 0.2s;
}

.info-icon:hover {
  color: var(--el-color-primary);
}

.readonly-registration-status {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
