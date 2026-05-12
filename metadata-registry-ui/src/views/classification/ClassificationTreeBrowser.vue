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
          <el-select
            v-model="currentSchemeId"
            placeholder="选择分类方案"
            filterable
            style="width: 100%;"
            @change="onSchemeChange"
          >
            <el-option
              v-for="s in schemes"
              :key="s.id"
              :label="s.schemeName"
              :value="s.id"
            />
          </el-select>
        </div>

        <div class="search-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索节点名称或编码"
            clearable
            :prefix-icon="Search"
            @keyup.enter="handleSearch"
          >
            <template #append>
              <el-button :icon="Search" @click="handleSearch" />
            </template>
          </el-input>
        </div>

        <div class="tree-area">
          <el-tree
            v-if="currentSchemeId"
            ref="treeRef"
            :data="treeData"
            :props="treeProps"
            :load="lazyLoad"
            :lazy="true"
            :highlight-current="true"
            :expand-on-click-node="false"
            node-key="id"
            @node-click="onNodeClick"
          >
            <template #default="{ node, data }">
              <span class="tree-node-label">
                <el-icon v-if="data.hasChildren"><Folder /></el-icon>
                <el-icon v-else><Document /></el-icon>
                <span class="node-text">{{ data.label }}</span>
                <el-tag v-if="data.nodeCode" size="small" type="info" effect="plain">
                  {{ data.nodeCode }}
                </el-tag>
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
          <el-empty description="点击左侧树节点查看详情" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Search, Folder, Document } from '@element-plus/icons-vue';
import request from '@/utils/request';

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

// State
const schemes = ref<Scheme[]>([]);
const currentSchemeId = ref<string>('');
const currentScheme = ref<Scheme | null>(null);
const treeData = ref<TreeNode[]>([]);
const treeRef = ref<any>(null);
const selectedNode = ref<NodeDetail | null>(null);
const selectedNodeBreadcrumb = ref<string>('');
const searchKeyword = ref('');
const parentNodeName = ref<string>('');

const treeProps = {
  children: 'children',
  label: 'label',
  isLeaf: (data: TreeNode) => !data.hasChildren,
};

// Fetch available schemes
async function fetchSchemes() {
  try {
    const res = await request.get('/classification-schemes', {
      params: { page: 0, size: 100 },
    });
    schemes.value = res.data.content || [];
  } catch {
    ElMessage.error('加载分类方案列表失败');
  }
}

// Scheme selection changed
async function onSchemeChange(schemeId: string) {
  selectedNode.value = null;
  selectedNodeBreadcrumb.value = '';
  parentNodeName.value = '';

  // Reset tree
  treeData.value = [];

  // Get scheme detail
  try {
    const res = await request.get(`/classification-schemes/${schemeId}`);
    currentScheme.value = res.data;
  } catch {
    currentScheme.value = null;
  }

  // Load root nodes
  await loadRootNodes();

  // Clear tree current highlight
  if (treeRef.value) {
    treeRef.value.setCurrentKey(null);
  }
}

// Lazy load children
async function lazyLoad(node: any, resolve: (data: TreeNode[]) => void) {
  if (!currentSchemeId.value) {
    resolve([]);
    return;
  }
  try {
    const res = await request.get(
      `/classification-schemes/${currentSchemeId.value}/nodes/children`,
      { params: { parentId: node.data.id } }
    );
    resolve(res.data || []);
  } catch {
    resolve([]);
  }
}

// Load root nodes directly
async function loadRootNodes() {
  if (!currentSchemeId.value) return;
  try {
    const res = await request.get(
      `/classification-schemes/${currentSchemeId.value}/nodes/children`
    );
    treeData.value = res.data || [];
  } catch {
    treeData.value = [];
  }
}

// Node click
async function onNodeClick(data: TreeNode) {
  if (!currentSchemeId.value) return;
  try {
    const res = await request.get(
      `/classification-schemes/${currentSchemeId.value}/nodes/${data.id}`
    );
    selectedNode.value = res.data;

    // Build breadcrumb
    selectedNodeBreadcrumb.value = data.label;

    // Get parent name if exists
    if (res.data.parentId) {
      try {
        const parentRes = await request.get(
          `/classification-schemes/${currentSchemeId.value}/nodes/${res.data.parentId}`
        );
        parentNodeName.value = parentRes.data.nodeName;
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

// Search nodes
async function handleSearch() {
  if (!currentSchemeId.value || !searchKeyword.value.trim()) {
    // If no keyword or no scheme, reload root
    await loadRootNodes();
    selectedNode.value = null;
    return;
  }

  try {
    const res = await request.get(
      `/classification-schemes/${currentSchemeId.value}/nodes/search`,
      { params: { keyword: searchKeyword.value.trim() } }
    );
    const results: NodeDetail[] = res.data || [];

    if (results.length === 0) {
      ElMessage.info('未找到匹配的节点');
      return;
    }

    // Highlight first result
    if (results.length > 0) {
      // Navigate to the tree path for this node by finding ancestors
      // For simplicity, just show the first result in detail panel
      selectedNode.value = results[0];
      selectedNodeBreadcrumb.value = results[0].nodeName;
      parentNodeName.value = '';

      // Try to find parent
      if (results[0].parentId) {
        try {
          const parentRes = await request.get(
            `/classification-schemes/${currentSchemeId.value}/nodes/${results[0].parentId}`
          );
          parentNodeName.value = parentRes.data.nodeName;
        } catch {
          // ignore
        }
      }
    }
  } catch {
    ElMessage.error('搜索失败');
  }
}

// Format date
function formatDate(dateStr?: string): string {
  if (!dateStr) return '-';
  const d = new Date(dateStr);
  return d.toLocaleString('zh-CN');
}

onMounted(() => {
  fetchSchemes();
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
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  border-bottom: 1px solid #f3f4f6;
}

.search-bar {
  padding: 8px 16px;
  border-bottom: 1px solid #f3f4f6;
}

.tree-area {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.tree-node-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;

  .node-text {
    max-width: 160px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.detail-panel {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  padding: 20px;
  overflow-y: auto;
}

.detail-header {
  margin-bottom: 16px;

  h3 {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #1f2937;
  }
}

.detail-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
