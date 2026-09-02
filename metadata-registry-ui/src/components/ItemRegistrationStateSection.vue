<template>
  <div class="item-registration-state-section">
    <!-- Registration_State 属性（ISO/IEC 11179-3:2023 表 22）：留空不变更，创建落默认（已记录/当前时间） -->
    <el-form-item label="注册状态">
      <el-select v-model="model.registrationStatus" placeholder="请选择" style="width: 100%;" clearable>
        <el-option
          v-for="s in REGISTRATION_STATUSES"
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
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n';

/** ISO/IEC 11179-3:2023 表 30 注册状态全量取值（按标准生命周期递进顺序排列）。 */
const REGISTRATION_STATUSES = [
  'INCOMPLETE', 'CANDIDATE', 'RECORDED', 'QUALIFIED', 'STANDARD',
  'PREFERRED_STANDARD', 'SUPERSEDED', 'RETIRED', 'HISTORICAL', 'APPLICATION',
] as const;

const { t } = useI18n();

/** 与宿主表单共享的 registration 对象（同 ItemRegistrationSection 的 RegistrationModel），
 *  状态字段由本组件编辑，提交负载仍由 ItemRegistrationSection.buildPayload 统一组装。 */
const model = defineModel<Record<string, any>>({ required: true });
</script>
