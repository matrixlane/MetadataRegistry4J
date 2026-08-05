<template>
  <div class="classification-tree-browser">
    <div class="page-toolbar">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>分类管理</el-breadcrumb-item>
        <el-breadcrumb-item v-if="currentScheme">
          {{ currentScheme.schemeName }}
        </el-breadcrumb-item>
        <el-breadcrumb-item v-if="selectedNodeBreadcrumb">
          {{ selectedNodeBreadcrumb }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="browser-container">
      <!-- Left Panel: Scheme selector + tree -->
      <div class="tree-panel">
        <div class="panel-header">
          <div class="scheme-select-row">
            <el-select
              v-model="currentSchemeId"
              placeholder="选择分类方案"
              filterable
              style="flex: 1;"
              @change="onSchemeChange"
            >
              <el-option
                v-for="s in schemes"
                :key="s.id"
                :label="s.schemeName"
                :value="s.id"
              />
            </el-select>
            <el-button
              type="primary"
              :icon="Plus"
              @click="openSchemeDialog('create')"
              title="新建分类方案"
            >
              新建方案
            </el-button>
          </div>

          <!-- Scheme Actions (Edit, Delete, Import, Export) -->
          <div v-if="currentScheme" class="scheme-actions-row">
            <el-button-group>
              <el-button
                size="small"
                :icon="Edit"
                @click="openSchemeDialog('edit')"
              >
                修改
              </el-button>
              <el-popconfirm
                title="确定删除此分类方案吗？只有在方案下没有节点时才能删除。"
                confirm-button-text="确定"
                cancel-button-text="取消"
                type="danger"
                @confirm="handleDeleteScheme"
              >
                <template #reference>
                  <el-button size="small" type="danger" :icon="Delete">
                    删除
                  </el-button>
                </template>
              </el-popconfirm>
              <el-button
                size="small"
                type="success"
                :icon="Upload"
                @click="openImportDialog"
              >
                导入
              </el-button>
            </el-button-group>

            <el-dropdown trigger="click" @command="handleExport">
              <el-button size="small" type="warning" :icon="Download">
                导出 <el-icon class="el-icon--right"><arrow-down /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="excel">导出 Excel (.xlsx)</el-dropdown-item>
                  <el-dropdown-item command="csv">导出 CSV (.csv)</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>

        <div class="search-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索节点名称或编码"
            clearable
            :prefix-icon="Search"
            @keyup.enter="handleSearch"
            @clear="handleSearchClear"
          >
            <template #append>
              <el-button :icon="Search" @click="handleSearch" />
            </template>
          </el-input>
        </div>

        <!-- Tree Toolbar -->
        <div v-if="currentSchemeId" class="tree-toolbar">
          <el-button
            size="small"
            type="primary"
            link
            :icon="Plus"
            @click="openNodeDialog('createRoot')"
          >
            新增根节点
          </el-button>
          <el-button
            size="small"
            type="info"
            link
            :icon="Refresh"
            @click="refreshTree"
          >
            刷新
          </el-button>
        </div>

        <!-- Tree Area -->
        <div class="tree-area">
          <el-tree
            v-if="currentSchemeId"
            ref="treeRef"
            :key="treeKey"
            :props="treeProps"
            :load="lazyLoad"
            :lazy="true"
            :highlight-current="true"
            :expand-on-click-node="false"
            node-key="id"
            @node-click="onNodeClick"
          >
            <template #default="{ node, data }">
              <span class="tree-node-wrapper">
                <span class="tree-node-label">
                  <el-icon v-if="data.hasChildren"><Folder /></el-icon>
                  <el-icon v-else><Document /></el-icon>
                  <span class="node-text">{{ data.label }}</span>
                  <el-tag v-if="data.nodeCode" size="small" type="info" effect="plain" class="node-code-tag">
                    {{ data.nodeCode }}
                  </el-tag>
                </span>
                
                <!-- Tree Node Hover Actions -->
                <span class="tree-node-actions" @click.stop>
                  <el-button
                    size="small"
                    type="primary"
                    link
                    :icon="Plus"
                    title="添加子节点"
                    @click="openNodeDialog('createChild', data)"
                  />
                  <el-button
                    size="small"
                    type="primary"
                    link
                    :icon="Edit"
                    title="编辑节点"
                    @click="openNodeDialog('edit', data)"
                  />
                  <el-button
                    size="small"
                    type="warning"
                    link
                    :icon="Rank"
                    title="移动节点"
                    @click="openMoveDialog(data)"
                  />
                  <el-popconfirm
                    title="确定删除此节点吗？若存在子节点则无法删除。"
                    confirm-button-text="确定"
                    cancel-button-text="取消"
                    type="warning"
                    @confirm="handleDeleteNode(data.id)"
                  >
                    <template #reference>
                      <el-button
                        size="small"
                        type="danger"
                        link
                        :icon="Delete"
                        title="删除节点"
                      />
                    </template>
                  </el-popconfirm>
                </span>
              </span>
            </template>
          </el-tree>
          <el-empty v-else description="请选择一个分类方案" />
        </div>
      </div>

      <!-- Right Panel: Node details -->
      <div class="detail-panel">
        <div v-if="selectedNode" class="node-detail">
          <div class="detail-header">
            <h3>节点详情</h3>
            <div class="node-operations">
              <el-button
                size="small"
                type="primary"
                :icon="Edit"
                @click="openNodeDialog('edit', selectedNode)"
              >
                编辑节点
              </el-button>
              <el-button
                size="small"
                type="success"
                :icon="Plus"
                @click="openNodeDialog('createChild', selectedNode)"
              >
                添加子节点
              </el-button>
              <el-button
                size="small"
                type="warning"
                :icon="Rank"
                @click="openMoveDialog(selectedNode)"
              >
                移动节点
              </el-button>
              
              <el-popconfirm
                title="确定删除此节点吗？(仅限无子节点)"
                confirm-button-text="确定"
                cancel-button-text="取消"
                type="warning"
                @confirm="handleDeleteNode(selectedNode.id)"
              >
                <template #reference>
                  <el-button size="small" type="danger" :icon="Delete">
                    删除
                  </el-button>
                </template>
              </el-popconfirm>

              <el-popconfirm
                title="确定级联删除此节点及其下所有后代节点吗？此操作无法恢复！"
                confirm-button-text="确定级联删除"
                cancel-button-text="取消"
                confirm-button-type="danger"
                type="danger"
                @confirm="handleDeleteNodeCascade(selectedNode.id)"
              >
                <template #reference>
                  <el-button size="small" type="danger" plain :icon="Delete">
                    级联删除
                  </el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="名称">
              {{ selectedNode.nodeName }}
            </el-descriptions-item>
            <el-descriptions-item label="编码">
              <el-tag>{{ selectedNode.nodeCode }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="所属方案">
              {{ selectedNode.schemeName }}
            </el-descriptions-item>
            <el-descriptions-item label="父节点">
              {{ parentNodeName || '（根节点）' }}
            </el-descriptions-item>
            <el-descriptions-item label="排序">
              {{ selectedNode.sortOrder }}
            </el-descriptions-item>
            <el-descriptions-item label="是否有子节点">
              <el-tag :type="selectedNode.hasChildren ? 'success' : 'info'" size="small">
                {{ selectedNode.hasChildren ? '是' : '否' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="描述">
              {{ selectedNode.description || '无' }}
            </el-descriptions-item>
            <el-descriptions-item label="定义">
              {{ selectedNode.definition || '无' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ formatDate(selectedNode.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ formatDate(selectedNode.updatedAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
        <div v-else class="detail-empty">
          <el-empty description="点击左侧树节点查看详情，或使用上方工具栏进行操作" />
        </div>
      </div>
    </div>

    <!-- Scheme Add/Edit Dialog -->
    <el-dialog
      v-model="schemeDialogVisible"
      :title="schemeFormTitle"
      width="500px"
      destroy-on-close
    >
      <el-form
        ref="schemeFormRef"
        :model="schemeForm"
        :rules="schemeRules"
        label-width="100px"
        style="padding-right: 20px;"
      >
        <el-form-item label="方案名称" prop="schemeName">
          <el-input v-model="schemeForm.schemeName" placeholder="例如：国际疾病分类ICD-10" />
        </el-form-item>
        <el-form-item label="版本" prop="version">
          <el-input v-model="schemeForm.version" placeholder="例如：2023 Edition" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="schemeForm.status" placeholder="选择状态" style="width: 100%;">
            <el-option label="草稿 (DRAFT)" value="DRAFT" />
            <el-option label="发布 (PUBLISHED)" value="PUBLISHED" />
            <el-option label="归档 (ARCHIVED)" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述说明" prop="description">
          <el-input
            v-model="schemeForm.description"
            type="textarea"
            :rows="3"
            placeholder="方案描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="schemeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSubmittingScheme" @click="submitSchemeForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- Node Add/Edit Dialog -->
    <el-dialog
      v-model="nodeDialogVisible"
      :title="nodeFormTitle"
      width="550px"
      destroy-on-close
    >
      <el-form
        ref="nodeFormRef"
        :model="nodeForm"
        :rules="nodeRules"
        label-width="100px"
        style="padding-right: 20px;"
      >
        <el-form-item v-if="nodeForm.parentId" label="父节点">
          <el-input :model-value="displayParentName" disabled />
        </el-form-item>
        <el-form-item label="节点编码" prop="nodeCode">
          <el-input
            v-model="nodeForm.nodeCode"
            placeholder="建议使用点分/阶梯编码，例如: A00.0"
            :disabled="isEditingNode"
          />
        </el-form-item>
        <el-form-item label="节点名称" prop="nodeName">
          <el-input v-model="nodeForm.nodeName" placeholder="中文或英文分类名称" />
        </el-form-item>
        <el-form-item label="排序值" prop="sortOrder">
          <el-input-number v-model="nodeForm.sortOrder" :min="0" style="width: 150px;" />
        </el-form-item>
        <el-form-item label="定义" prop="definition">
          <el-input
            v-model="nodeForm.definition"
            type="textarea"
            :rows="2"
            placeholder="标准定义"
          />
        </el-form-item>
        <el-form-item label="详细描述" prop="description">
          <el-input
            v-model="nodeForm.description"
            type="textarea"
            :rows="3"
            placeholder="关于分类项目的备注与补充描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSubmittingNode" @click="submitNodeForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- Move Node Dialog -->
    <el-dialog
      v-model="moveDialogVisible"
      title="移动分类节点"
      width="480px"
    >
      <div class="move-dialog-content">
        <p class="warning-text">
          正在移动节点：<strong>{{ moveNodeName }}</strong>。此操作将调整该节点及其子树的所有层级归属。
        </p>
        <el-form label-width="100px">
          <el-form-item label="选择父节点">
            <el-select
              v-model="moveTargetParentId"
              placeholder="选择新的父分类项目，留空代表移至根节点"
              clearable
              filterable
              style="width: 100%;"
            >
              <el-option
                label="（设置为根节点 / 无父节点）"
                :value="(null as any)"
              />
              <el-option
                v-for="node in availableNodesForMove"
                :key="node.id"
                :label="`${node.nodeCode} - ${node.nodeName}`"
                :value="node.id"
              />
            </el-select>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="isMoving" @click="submitMoveNode">确定移动</el-button>
      </template>
    </el-dialog>

    <!-- Import Dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入分类方案"
      width="750px"
      :before-close="handleImportClose"
    >
      <div class="import-steps">
        <el-steps :active="importStep" align-center finish-status="success" style="margin-bottom: 24px;">
          <el-step title="上传文件与校验" />
          <el-step title="查看校验结果" />
          <el-step title="导入完成" />
        </el-steps>
      </div>

      <!-- Step 1: Upload and options -->
      <div v-if="importStep === 1" class="import-step-content">
        <el-form label-width="110px">
          <el-form-item label="导入目标">
            <el-radio-group v-model="importTargetType">
              <el-radio value="new">作为新方案导入 (解析文件头 scheme_name)</el-radio>
              <el-radio :disabled="!currentSchemeId" value="current">
                导入到当前方案：{{ currentScheme?.schemeName || '未选择' }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="选择文件" required>
            <el-upload
              ref="uploadRef"
              class="upload-area"
              drag
              action=""
              :auto-upload="false"
              :limit="1"
              accept=".xlsx,.csv"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
            >
              <el-icon class="el-icon--upload"><upload-filled /></el-icon>
              <div class="el-upload__text">
                将文件拖到此处，或<em>点击上传</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持 Excel (.xlsx) 或 CSV (.csv) 格式，文件大小不超过 10MB。
                </div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: Show validate report -->
      <div v-if="importStep === 2" class="import-step-content">
        <div v-if="isValidating" class="validation-loading">
          <el-skeleton :rows="5" animated />
          <div class="loading-overlay-text">正在预校验导入数据，请稍候...</div>
        </div>
        <div v-else-if="importResult" class="validation-report">
          <el-alert
            v-if="importResult.success"
            title="预校验通过"
            type="success"
            description="文件格式和依赖关系均完整无误，可以安全导入数据库。"
            show-icon
            :closable="false"
          />
          <el-alert
            v-else
            title="预校验未通过"
            type="error"
            :description="`解析到共计 ${importResult.errorCount} 项数据错误，无法提交导入。请修改后重新上传。`"
            show-icon
            :closable="false"
          />

          <div class="report-summary">
            <el-row :gutter="20">
              <el-col :span="6">
                <div class="summary-box">
                  <div class="summary-label">方案名称</div>
                  <div class="summary-value">{{ importResult.schemeName || '-' }}</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="summary-box">
                  <div class="summary-label">总行数</div>
                  <div class="summary-value">{{ importResult.totalRows }}</div>
                </div>
              </el-col>
              <el-col :span="6">
                <div class="summary-box">
                  <div class="summary-label">错误项</div>
                  <div class="summary-value error">{{ importResult.errorCount }}</div>
                </div>
              </el-col>
            </el-row>
          </div>

          <div v-if="importResult.errors && importResult.errors.length > 0" class="report-table">
            <h4>错误明细</h4>
            <el-table :data="importResult.errors" size="small" border max-height="250px" style="width: 100%;">
              <el-table-column prop="rowNumber" label="行号" width="70" align="center" />
              <el-table-column prop="columnName" label="关联列" width="150" />
              <el-table-column prop="errorMessage" label="错误描述" />
              <el-table-column prop="suggestion" label="修复建议" width="200" />
            </el-table>
          </div>
        </div>
      </div>

      <!-- Step 3: Complete -->
      <div v-if="importStep === 3" class="import-step-content text-center">
        <div v-if="isImporting" class="importing-spinner">
          <el-skeleton :rows="3" animated />
          <div class="loading-overlay-text">正在写入数据库，请稍候...</div>
        </div>
        <div v-else class="import-success-panel">
          <el-icon class="success-icon" color="#67C23A" size="64px"><CircleCheck /></el-icon>
          <h2>导入成功！</h2>
          <p>分类方案及其节点数据已成功提交数据库。</p>
          <div class="success-stats">
            <p><strong>最终导入分类方案：</strong>{{ importResult?.schemeName }}</p>
            <p><strong>共导入分类项目数量：</strong><el-tag type="success">{{ importResult?.importedNodes }}</el-tag> 个</p>
          </div>
        </div>
      </div>

      <template #footer>
        <!-- Step 1 Footers -->
        <span v-if="importStep === 1">
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="!selectedFile" @click="triggerValidate">开始校验</el-button>
        </span>
        <!-- Step 2 Footers -->
        <span v-if="importStep === 2">
          <el-button :disabled="isValidating" @click="importStep = 1">上一步</el-button>
          <el-button
            type="primary"
            :disabled="isValidating || !importResult?.success"
            @click="triggerImport"
          >
            确认导入
          </el-button>
        </span>
        <!-- Step 3 Footers -->
        <span v-if="importStep === 3">
          <el-button type="primary" :disabled="isImporting" @click="handleImportComplete">关闭并刷新</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  Search, Folder, Document, Plus, Edit, Delete, Rank,
  Upload, Download, Refresh, ArrowDown, UploadFilled,
  CircleCheck
} from '@element-plus/icons-vue';
import { classificationSchemeApi } from '@/api/resources';
import axios from 'axios';
import { useAuthStore } from '@/stores/auth';

// Types
interface Scheme {
  id: string;
  schemeName: string;
  description?: string;
  version?: string;
  status?: string;
}

interface TreeNode {
  id: string;
  label: string;
  nodeCode: string;
  description?: string;
  sortOrder: number;
  hasChildren: boolean;
  children?: TreeNode[];
}

interface NodeDetail {
  id: string;
  nodeCode: string;
  nodeName: string;
  description?: string;
  definition?: string;
  sortOrder: number;
  parentId?: string;
  schemeId: string;
  schemeName: string;
  hasChildren: boolean;
  createdAt: string;
  updatedAt: string;
}

// Tree structure props
const treeProps: import('element-plus/es/components/tree/src/tree.type').TreeOptionProps = {
  children: 'children',
  label: 'label',
  isLeaf: (data: any) => !data.hasChildren,
};

// Core States
const schemes = ref<Scheme[]>([]);
const currentSchemeId = ref<string>('');
const currentScheme = ref<Scheme | null>(null);
const treeRef = ref<any>(null);
const treeKey = ref<number>(1);
const selectedNode = ref<NodeDetail | null>(null);
const selectedNodeBreadcrumb = ref<string>('');
const searchKeyword = ref('');
const parentNodeName = ref<string>('');

// Scheme dialog states
const schemeDialogVisible = ref(false);
const schemeFormTitle = ref('');
const isSubmittingScheme = ref(false);
const schemeFormRef = ref<any>(null);
const schemeForm = ref({
  id: '',
  schemeName: '',
  version: '',
  status: 'DRAFT',
  description: ''
});

// Node dialog states
const nodeDialogVisible = ref(false);
const nodeFormTitle = ref('');
const isSubmittingNode = ref(false);
const isEditingNode = ref(false);
const nodeFormRef = ref<any>(null);
const displayParentName = ref('');
const nodeForm = ref({
  id: '',
  nodeCode: '',
  nodeName: '',
  sortOrder: 0,
  definition: '',
  description: '',
  parentId: null as string | null
});

// Move dialog states
const moveDialogVisible = ref(false);
const moveNodeId = ref('');
const moveNodeName = ref('');
const moveTargetParentId = ref<string | null>(null);
const isMoving = ref(false);
const flatNodes = ref<any[]>([]);

// Import states
const importDialogVisible = ref(false);
const importStep = ref(1);
const importTargetType = ref<'new' | 'current'>('new');
const selectedFile = ref<File | null>(null);
const uploadRef = ref<any>(null);
const isValidating = ref(false);
const isImporting = ref(false);
const importResult = ref<any>(null);

// Forms validation rules
const schemeRules = {
  schemeName: [
    { required: true, message: '方案名称不能为空', trigger: 'blur' },
    { max: 255, message: '方案名称不能超过255个字符', trigger: 'blur' }
  ],
  version: [
    { max: 50, message: '版本号不能超过50个字符', trigger: 'blur' }
  ],
  status: [
    { max: 20, message: '状态不能超过20个字符', trigger: 'blur' }
  ],
  description: [
    { max: 4000, message: '描述不能超过4000个字符', trigger: 'blur' }
  ]
};

const nodeRules = {
  nodeCode: [
    { required: true, message: '节点编码不能为空', trigger: 'blur' },
    { max: 255, message: '节点编码不能超过255个字符', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_\-\.]+$/, message: '节点编码只能包含字母、数字、下划线、连字符和点号', trigger: 'blur' }
  ],
  nodeName: [
    { required: true, message: '节点名称不能为空', trigger: 'blur' },
    { max: 255, message: '节点名称不能超过255个字符', trigger: 'blur' }
  ],
  sortOrder: [
    { type: 'number', message: '排序号必须为数字', trigger: 'blur' }
  ],
  description: [
    { max: 4000, message: '描述不能超过4000个字符', trigger: 'blur' }
  ],
  definition: [
    { max: 4000, message: '定义不能超过4000个字符', trigger: 'blur' }
  ]
};

// Available parent nodes for movement (excludes target itself)
const availableNodesForMove = computed(() => {
  return flatNodes.value.filter(node => node.id !== moveNodeId.value);
});

// Fetch classification schemes
async function fetchSchemes(selectFirst = false) {
  try {
    const res = await classificationSchemeApi.list({ page: 0, size: 100 });
    schemes.value = res.content || [];
    if (selectFirst && schemes.value.length > 0) {
      currentSchemeId.value = schemes.value[0].id;
      onSchemeChange(currentSchemeId.value);
    }
  } catch {
    ElMessage.error('加载分类方案列表失败');
  }
}

// Scheme selection change handler
async function onSchemeChange(schemeId: string) {
  selectedNode.value = null;
  selectedNodeBreadcrumb.value = '';
  parentNodeName.value = '';
  searchKeyword.value = '';

  // Reset tree
  treeKey.value++;

  // Fetch scheme details
  try {
    const res = await classificationSchemeApi.get(schemeId);
    currentScheme.value = res;
  } catch {
    currentScheme.value = null;
  }
}

// Force tree refresh
function refreshTree() {
  treeKey.value++;
  selectedNode.value = null;
  selectedNodeBreadcrumb.value = '';
  parentNodeName.value = '';
}

// Lazy load tree nodes
async function lazyLoad(node: any, resolve: (data: TreeNode[]) => void) {
  if (!currentSchemeId.value) {
    resolve([]);
    return;
  }
  const parentId = node.level === 0 ? undefined : node.data.id;
  try {
    const res = await classificationSchemeApi.getChildren(currentSchemeId.value, parentId);
    resolve(res || []);
  } catch {
    resolve([]);
  }
}

// Click on tree node
async function onNodeClick(data: TreeNode) {
  if (!currentSchemeId.value) return;
  try {
    const res = await classificationSchemeApi.getNode(currentSchemeId.value, data.id);
    selectedNode.value = res;
    selectedNodeBreadcrumb.value = data.label;

    if (res.parentId) {
      try {
        const parentRes = await classificationSchemeApi.getNode(currentSchemeId.value, res.parentId);
        parentNodeName.value = parentRes.nodeName;
      } catch {
        parentNodeName.value = '';
      }
    } else {
      parentNodeName.value = '';
    }
  } catch {
    ElMessage.error('加载节点详情失败');
  }
}

// Search nodes inside the selected scheme
async function handleSearch() {
  if (!currentSchemeId.value || !searchKeyword.value.trim()) {
    refreshTree();
    return;
  }

  try {
    const res = await classificationSchemeApi.searchNodes(currentSchemeId.value, searchKeyword.value.trim());
    const results: NodeDetail[] = res || [];

    if (results.length === 0) {
      ElMessage.info('未找到匹配的节点');
      return;
    }

    // Since displaying highlght path in lazy tree is complex,
    // we highlight the first search match in the right detail panel.
    selectedNode.value = results[0];
    selectedNodeBreadcrumb.value = results[0].nodeName;
    parentNodeName.value = '';

    if (results[0].parentId) {
      try {
        const parentRes = await classificationSchemeApi.getNode(currentSchemeId.value, results[0].parentId);
        parentNodeName.value = parentRes.nodeName;
      } catch {
        // ignore
      }
    }
  } catch {
    ElMessage.error('搜索节点失败');
  }
}

function handleSearchClear() {
  refreshTree();
}

// ==================== Scheme Operations ====================

function openSchemeDialog(type: 'create' | 'edit') {
  if (type === 'create') {
    schemeFormTitle.value = '新建分类方案';
    schemeForm.value = {
      id: '',
      schemeName: '',
      version: 'v1.0',
      status: 'DRAFT',
      description: ''
    };
  } else {
    if (!currentScheme.value) return;
    schemeFormTitle.value = '修改分类方案';
    schemeForm.value = {
      id: currentScheme.value.id,
      schemeName: currentScheme.value.schemeName,
      version: currentScheme.value.version || '',
      status: currentScheme.value.status || 'DRAFT',
      description: currentScheme.value.description || ''
    };
  }
  schemeDialogVisible.value = true;
  nextTick(() => {
    schemeFormRef.value?.clearValidate();
  });
}

async function submitSchemeForm() {
  if (!schemeFormRef.value) return;
  await schemeFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    isSubmittingScheme.value = true;
    try {
      if (schemeForm.value.id) {
        await classificationSchemeApi.update(schemeForm.value.id, schemeForm.value);
        ElMessage.success('修改分类方案成功');
      } else {
        const newScheme = await classificationSchemeApi.create(schemeForm.value);
        ElMessage.success('新建分类方案成功');
        currentSchemeId.value = newScheme.id;
      }
      schemeDialogVisible.value = false;
      await fetchSchemes();
      if (currentSchemeId.value) {
        await onSchemeChange(currentSchemeId.value);
      }
    } catch (error) {
      // errors handled by interceptor
    } finally {
      isSubmittingScheme.value = false;
    }
  });
}

async function handleDeleteScheme() {
  if (!currentSchemeId.value) return;
  try {
    await classificationSchemeApi.delete(currentSchemeId.value);
    ElMessage.success('删除分类方案成功');
    currentSchemeId.value = '';
    currentScheme.value = null;
    selectedNode.value = null;
    selectedNodeBreadcrumb.value = '';
    await fetchSchemes(true);
  } catch (error) {
    // Error caught by interceptor
  }
}

// ==================== Node Operations ====================

async function openNodeDialog(type: 'createRoot' | 'createChild' | 'edit', targetNode?: any) {
  isEditingNode.value = type === 'edit';
  if (type === 'createRoot') {
    nodeFormTitle.value = '新增根分类节点';
    displayParentName.value = '';
    nodeForm.value = {
      id: '',
      nodeCode: '',
      nodeName: '',
      sortOrder: 10,
      definition: '',
      description: '',
      parentId: null
    };
  } else if (type === 'createChild') {
    nodeFormTitle.value = '新增子分类节点';
    displayParentName.value = targetNode.nodeName || targetNode.label || '当前节点';
    nodeForm.value = {
      id: '',
      nodeCode: '',
      nodeName: '',
      sortOrder: 10,
      definition: '',
      description: '',
      parentId: targetNode.id
    };
  } else {
    // edit
    nodeFormTitle.value = '编辑分类节点';
    const detail = targetNode.nodeCode ? targetNode : await classificationSchemeApi.getNode(currentSchemeId.value, targetNode.id);
    displayParentName.value = '';
    nodeForm.value = {
      id: detail.id,
      nodeCode: detail.nodeCode,
      nodeName: detail.nodeName,
      sortOrder: detail.sortOrder,
      definition: detail.definition || '',
      description: detail.description || '',
      parentId: detail.parentId || null
    };
  }
  nodeDialogVisible.value = true;
  nextTick(() => {
    nodeFormRef.value?.clearValidate();
  });
}

async function submitNodeForm() {
  if (!nodeFormRef.value) return;
  await nodeFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    isSubmittingNode.value = true;
    try {
      if (nodeForm.value.id) {
        await classificationSchemeApi.updateNode(currentSchemeId.value, nodeForm.value.id, nodeForm.value);
        ElMessage.success('编辑节点成功');
        const res = await classificationSchemeApi.getNode(currentSchemeId.value, nodeForm.value.id);
        selectedNode.value = res;
        selectedNodeBreadcrumb.value = res.nodeName;
      } else {
        await classificationSchemeApi.createNode(currentSchemeId.value, nodeForm.value);
        ElMessage.success('创建节点成功');
        selectedNode.value = null;
        selectedNodeBreadcrumb.value = '';
      }
      nodeDialogVisible.value = false;
      treeKey.value++; // reload tree
    } catch {
      // error handled by interceptor
    } finally {
      isSubmittingNode.value = false;
    }
  });
}

async function handleDeleteNode(nodeId: string) {
  try {
    await classificationSchemeApi.deleteNode(currentSchemeId.value, nodeId);
    ElMessage.success('删除节点成功');
    treeKey.value++;
    selectedNode.value = null;
    selectedNodeBreadcrumb.value = '';
  } catch {
    // handled by interceptor
  }
}

async function handleDeleteNodeCascade(nodeId: string) {
  try {
    await classificationSchemeApi.deleteNodeCascade(currentSchemeId.value, nodeId);
    ElMessage.success('级联删除节点成功');
    treeKey.value++;
    selectedNode.value = null;
    selectedNodeBreadcrumb.value = '';
  } catch {
    // handled by interceptor
  }
}

// ==================== Node Movement ====================

async function openMoveDialog(node: any) {
  moveNodeId.value = node.id;
  moveNodeName.value = node.nodeName || node.label || '';
  moveTargetParentId.value = null;
  isMoving.value = false;

  try {
    // Fetch all nodes inside the scheme to populate selection
    const res = await classificationSchemeApi.listNodes(currentSchemeId.value);
    flatNodes.value = res || [];
    moveDialogVisible.value = true;
  } catch {
    ElMessage.error('加载同级分类列表失败');
  }
}

async function submitMoveNode() {
  isMoving.value = true;
  try {
    await classificationSchemeApi.moveNode(
      currentSchemeId.value,
      moveNodeId.value,
      moveTargetParentId.value
    );
    ElMessage.success('移动节点层级成功');
    moveDialogVisible.value = false;
    treeKey.value++;
    if (selectedNode.value && selectedNode.value.id === moveNodeId.value) {
      const res = await classificationSchemeApi.getNode(currentSchemeId.value, moveNodeId.value);
      selectedNode.value = res;
      selectedNodeBreadcrumb.value = res.nodeName;
    } else {
      selectedNode.value = null;
      selectedNodeBreadcrumb.value = '';
    }
  } catch {
    // Error handled by interceptor
  } finally {
    isMoving.value = false;
  }
}

// ==================== Import & Export ====================

async function handleExport(format: string) {
  if (!currentSchemeId.value) return;
  try {
    const token = useAuthStore().token;
    const baseURL = import.meta.env.VITE_API_BASE ?? '/api/v1';
    // Direct Axios request with Blob output to bypass standard API interceptor unwrapping
    const response = await axios.get(`${baseURL}/classification-schemes/${currentSchemeId.value}/export`, {
      params: { format },
      headers: {
        Authorization: `Bearer ${token}`
      },
      responseType: 'blob'
    });

    const blob = new Blob([response.data], { type: (response.headers['content-type'] as string | undefined) });
    const filename = format === 'csv'
      ? `classification-scheme-${currentSchemeId.value}.csv`
      : `classification-scheme-${currentSchemeId.value}.xlsx`;

    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = filename;
    link.click();
    URL.revokeObjectURL(link.href);
    ElMessage.success('导出数据包完成');
  } catch (error) {
    console.error(error);
    ElMessage.error('下载导出文件失败');
  }
}

function openImportDialog() {
  importStep.value = 1;
  importTargetType.value = currentSchemeId.value ? 'current' : 'new';
  selectedFile.value = null;
  importResult.value = null;
  isValidating.value = false;
  isImporting.value = false;
  importDialogVisible.value = true;
  nextTick(() => {
    uploadRef.value?.clearFiles();
  });
}

function handleFileChange(file: any) {
  selectedFile.value = file.raw;
}

function handleFileRemove() {
  selectedFile.value = null;
}

// Phase 1: Validate import
async function triggerValidate() {
  if (!selectedFile.value) return;
  importStep.value = 2;
  isValidating.value = true;
  importResult.value = null;

  try {
    const targetScheme = importTargetType.value === 'current' ? currentSchemeId.value : undefined;
    const res = await classificationSchemeApi.validateImport(selectedFile.value, targetScheme);
    importResult.value = res;
  } catch {
    importStep.value = 1;
  } finally {
    isValidating.value = false;
  }
}

// Phase 2: Execute actual import
async function triggerImport() {
  if (!selectedFile.value) return;
  importStep.value = 3;
  isImporting.value = true;

  try {
    const targetScheme = importTargetType.value === 'current' ? currentSchemeId.value : undefined;
    const res = await classificationSchemeApi.importFile(selectedFile.value, targetScheme);
    importResult.value = res;
  } catch {
    importStep.value = 2;
  } finally {
    isImporting.value = false;
  }
}

function handleImportComplete() {
  importDialogVisible.value = false;
  // If we imported under a scheme, or created a new scheme
  fetchSchemes();
  if (importResult.value?.schemeId) {
    currentSchemeId.value = importResult.value.schemeId;
    onSchemeChange(currentSchemeId.value);
  } else {
    refreshTree();
  }
}

function handleImportClose(done: () => void) {
  if (importStep.value === 3 && isImporting.value) {
    // block closing while writing to DB
    return;
  }
  done();
}

// Helpers
function formatDate(dateStr?: string): string {
  if (!dateStr) return '-';
  const d = new Date(dateStr);
  return d.toLocaleString('zh-CN');
}

onMounted(() => {
  fetchSchemes(true);
});
</script>

<style scoped lang="scss">
.classification-tree-browser {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-toolbar {
  margin-bottom: 16px;
}

.browser-container {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.tree-panel {
  width: 380px;
  min-width: 320px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #f3f4f6;
  background-color: #fafafa;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.scheme-select-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.scheme-actions-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
}

.search-bar {
  padding: 10px 16px;
  border-bottom: 1px solid #f3f4f6;
}

.tree-toolbar {
  padding: 4px 16px;
  border-bottom: 1px solid #f3f4f6;
  background-color: #fbfbfb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tree-area {
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px;
}

.tree-node-wrapper {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}

.tree-node-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #374151;

  .node-text {
    max-width: 130px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .node-code-tag {
    margin-left: 4px;
    font-size: 11px;
    height: 18px;
    line-height: 16px;
    padding: 0 4px;
  }
}

.tree-node-actions {
  display: none;
  align-items: center;
  gap: 2px;
}

.el-tree-node__content:hover {
  .tree-node-actions {
    display: inline-flex;
  }
}

.detail-panel {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  padding: 24px;
  overflow-y: auto;
}

.detail-header {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 12px;

  h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: #1f2937;
  }

  .node-operations {
    display: flex;
    gap: 8px;
  }
}

.detail-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.move-dialog-content {
  padding: 10px 0;
}

.warning-text {
  color: #e6a23c;
  margin-bottom: 16px;
  line-height: 1.5;
  background-color: #fdf6ec;
  padding: 10px 14px;
  border-radius: 4px;
  border-left: 4px solid #e6a23c;
}

.upload-area {
  width: 100%;
}

.validation-loading, .importing-spinner {
  padding: 40px 20px;
  text-align: center;
}

.loading-overlay-text {
  margin-top: 16px;
  font-size: 14px;
  color: #606266;
}

.validation-report {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-summary {
  margin: 8px 0;
}

.summary-box {
  background-color: #f8f9fa;
  padding: 12px;
  border-radius: 6px;
  border: 1px solid #e9ecef;
  text-align: center;

  .summary-label {
    font-size: 12px;
    color: #6c757d;
    margin-bottom: 4px;
  }

  .summary-value {
    font-size: 16px;
    font-weight: bold;
    color: #495057;

    &.error {
      color: #f56c6c;
    }
  }
}

.report-table {
  h4 {
    margin: 0 0 10px 0;
    font-size: 14px;
    color: #303133;
  }
}

.import-success-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 30px;

  h2 {
    margin: 16px 0 8px 0;
    color: #303133;
  }

  p {
    color: #606266;
    margin-bottom: 24px;
  }

  .success-stats {
    background-color: #f0f9eb;
    border: 1px solid #e1f3d8;
    padding: 16px 24px;
    border-radius: 8px;
    width: 80%;
    max-width: 400px;
    text-align: left;

    p {
      margin: 8px 0;
      font-size: 14px;
      color: #606266;
    }
  }
}

.text-center {
  text-align: center;
}
</style>
