<template>
  <div class="item-registration-state-section">
    <!-- Registration_State 属性（ISO/IEC 11179-3:2023 表 22）：留空不变更，创建落默认（已记录/当前时间） -->
    <el-form-item label="注册状态">
      <template v-if="isEditing">
        <div class="readonly-registration-status" data-test="readonly-registration-status">
          <el-tag size="small" data-test="readonly-status-tag">
            {{ model.registrationStatus ? t('registrationStatus.' + model.registrationStatus) : '-' }}
          </el-tag>
          <span v-if="model.effectiveDate" class="effective-date" data-test="readonly-effective-date">
            {{ t('itemRegistrationState.effectiveAt', { date: model.effectiveDate }) }}
          </span>
        </div>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          :title="t('itemRegistrationState.editHint')"
          class="readonly-hint"
          data-test="readonly-status-hint"
        />
      </template>
      <!-- 新建模式的初始状态白名单与后端 ALLOWED_INITIAL_STATUSES 对齐：仅 INCOMPLETE（不完整）、
           APPLICATION（应用）、HISTORICAL（历史）；生命周期态须经推进流程获得 -->
      <el-select v-else v-model="model.registrationStatus" placeholder="请选择" style="width: 100%;" clearable>
        <el-option
          v-for="s in INITIAL_REGISTRATION_STATUSES"
          :key="s"
          :label="t(`registrationStatus.${s}`)"
          :value="s"
        />
      </el-select>
    </el-form-item>
    <el-form-item label="管理状态">
      <el-input v-model="model.administrativeStatus" placeholder="如 Approved（自由文本，留空不变更）" clearable />
    </el-form-item>
    <el-form-item label="生效日期">
      <el-date-picker
        v-model="model.effectiveDate"
        type="datetime"
        placeholder="请选择"
        style="width: 100%;"
        value-format="YYYY-MM-DDTHH:mm:ss"
        clearable
      />
    </el-form-item>
    <el-form-item label="截止日期">
      <el-date-picker
        v-model="model.untilDate"
        type="datetime"
        placeholder="请选择"
        style="width: 100%;"
        value-format="YYYY-MM-DDTHH:mm:ss"
        clearable
      />
    </el-form-item>
    <el-form-item label="管理备注">
      <el-input v-model="model.administrativeNote" type="textarea" :rows="2" placeholder="留空不变更" />
    </el-form-item>
    <el-form-item label="未决问题">
      <el-input v-model="model.unresolvedIssue" type="textarea" :rows="2" placeholder="留空不变更" />
    </el-form-item>
    <el-form-item :label="t('assistant.explanatoryComment')">
      <el-input
        v-model="model.explanatoryComment"
        type="textarea"
        :rows="3"
        data-test="explanatory-comment"
        :placeholder="t('assistant.explanatoryCommentPlaceholder')"
      />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';

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
.readonly-registration-status {
  display: flex;
  align-items: center;
  gap: 8px;

  .effective-date {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.readonly-hint {
  margin-top: 8px;
  width: 100%;
}
</style>
