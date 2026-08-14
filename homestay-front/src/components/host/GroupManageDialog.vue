<template>
  <!-- 分组管理对话框 -->
  <el-dialog
    :model-value="modelValue"
    title="房源分组管理"
    width="60%"
    top="5vh"
    @update:model-value="emit('update:modelValue', $event)"
    @open="fetchGroups"
  >
    <div class="group-manage-content">
      <div class="group-header">
        <el-button type="primary" size="small" @click="openGroupFormDialog()">
          <el-icon><Plus /></el-icon> 新建分组
        </el-button>
      </div>
      <el-table :data="groups" v-loading="groupsLoading" stripe>
        <el-table-column prop="name" label="分组名称" min-width="120" />
        <el-table-column prop="code" label="编码" width="100" />
        <el-table-column prop="homestayCount" label="房源数" width="80" />
        <el-table-column prop="sortOrder" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openGroupFormDialog(row)">编辑</el-button>
            <el-button v-if="!row.isDefault" size="small" type="danger" @click="handleDeleteGroup(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>

  <!-- 分组表单对话框 -->
  <el-dialog
    v-model="groupFormDialogVisible"
    :title="groupForm.id ? '编辑分组' : '新建分组'"
    width="500px"
  >
    <el-form :model="groupForm" label-width="80px">
      <el-form-item label="分组名称" required>
        <el-input v-model="groupForm.name" placeholder="请输入分组名称" />
      </el-form-item>
      <el-form-item label="分组编码">
        <el-input v-model="groupForm.code" placeholder="英文标识，如 sea-view" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="groupForm.description" type="textarea" :rows="3" placeholder="分组描述" />
      </el-form-item>
      <el-form-item label="图标">
        <el-input v-model="groupForm.icon" placeholder="图标名称" />
      </el-form-item>
      <el-form-item label="颜色">
        <el-color-picker v-model="groupForm.color" />
      </el-form-item>
      <el-form-item label="排序">
        <el-input-number v-model="groupForm.sortOrder" :min="0" :max="999" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="groupForm.enabled" active-text="启用" inactive-text="禁用" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="groupFormDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleSaveGroup" :loading="groupFormLoading">保存</el-button>
    </template>
  </el-dialog>

  <!-- 批量分组对话框 -->
  <el-dialog v-model="batchGroupDialogVisible" title="批量分配分组" width="400px">
    <p>已选择 <strong>{{ selectedRows.length }}</strong> 个房源</p>
    <el-form style="margin-top: 16px;">
      <el-form-item label="选择分组">
        <el-select v-model="batchGroupTargetId" placeholder="请选择分组" style="width: 100%;">
          <el-option label="移除分组（未分组）" :value="0" />
          <el-option v-for="g in groups" :key="g.id" :label="g.name" :value="g.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="batchGroupDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="handleBatchGroup" :loading="batchGroupLoading">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import {
  getHomestayGroups,
  createHomestayGroup,
  updateHomestayGroup,
  deleteHomestayGroup,
  batchAssignToGroup,
  batchRemoveFromGroup,
} from "@/api/homestay";
import type { HomestayGroup } from "@/types";

const props = defineProps<{
  modelValue: boolean;
  selectedRows: { id: number }[];
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  groupsChanged: [];
}>();

const groups = ref<HomestayGroup[]>([]);
const groupsLoading = ref(false);

const groupFormDialogVisible = ref(false);
const groupFormLoading = ref(false);
const groupForm = ref({
  id: undefined as number | undefined,
  name: "",
  code: "",
  description: "",
  icon: "",
  color: "#409eff",
  sortOrder: 0,
  enabled: true,
});

const batchGroupDialogVisible = ref(false);
const batchGroupTargetId = ref<number | undefined>(undefined);
const batchGroupLoading = ref(false);

const fetchGroups = async () => {
  try {
    groupsLoading.value = true;
    groups.value = await getHomestayGroups();
  } catch (error) {
    console.error("获取分组列表失败:", error);
  } finally {
    groupsLoading.value = false;
  }
};

const openGroupFormDialog = (group?: HomestayGroup) => {
  if (group) {
    groupForm.value = {
      id: group.id,
      name: group.name,
      code: group.code || "",
      description: group.description || "",
      icon: group.icon || "",
      color: group.color || "#409eff",
      sortOrder: group.sortOrder,
      enabled: group.enabled,
    };
  } else {
    groupForm.value = {
      id: undefined,
      name: "",
      code: "",
      description: "",
      icon: "",
      color: "#409eff",
      sortOrder: 0,
      enabled: true,
    };
  }
  groupFormDialogVisible.value = true;
};

const handleSaveGroup = async () => {
  if (!groupForm.value.name.trim()) {
    ElMessage.warning("分组名称不能为空");
    return;
  }
  try {
    groupFormLoading.value = true;
    const payload = {
      name: groupForm.value.name,
      code: groupForm.value.code || undefined,
      description: groupForm.value.description || undefined,
      icon: groupForm.value.icon || undefined,
      color: groupForm.value.color || undefined,
      sortOrder: groupForm.value.sortOrder,
      enabled: groupForm.value.enabled,
    };
    if (groupForm.value.id) {
      await updateHomestayGroup(groupForm.value.id, payload);
      ElMessage.success("分组更新成功");
    } else {
      await createHomestayGroup(payload);
      ElMessage.success("分组创建成功");
    }
    groupFormDialogVisible.value = false;
    await fetchGroups();
    emit("groupsChanged");
  } catch (error: any) {
    console.error("保存分组失败:", error);
  } finally {
    groupFormLoading.value = false;
  }
};

const handleDeleteGroup = async (id: number) => {
  try {
    await ElMessageBox.confirm("删除分组后，该分组下的房源将变为未分组状态，确定要删除吗？", "删除确认", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    });
    await deleteHomestayGroup(id);
    ElMessage.success("分组已删除");
    await fetchGroups();
    emit("groupsChanged");
  } catch (error: any) {
    if (error !== "cancel") {
      console.error("删除分组失败:", error);
    }
  }
};

const openBatchGroupDialog = () => {
  if (props.selectedRows.length === 0) {
    ElMessage.warning("请至少选择一个房源");
    return;
  }
  batchGroupTargetId.value = undefined;
  batchGroupDialogVisible.value = true;
  fetchGroups();
};

const handleBatchGroup = async () => {
  if (batchGroupTargetId.value === undefined) {
    ElMessage.warning("请选择目标分组");
    return;
  }
  try {
    batchGroupLoading.value = true;
    const ids = props.selectedRows.map((item) => item.id);
    if (batchGroupTargetId.value === 0) {
      await batchRemoveFromGroup(ids);
      ElMessage.success("已移除分组");
    } else {
      await batchAssignToGroup(batchGroupTargetId.value, ids);
      ElMessage.success("批量分组成功");
    }
    batchGroupDialogVisible.value = false;
    emit("groupsChanged");
  } catch (error: any) {
    console.error("批量分组失败:", error);
  } finally {
    batchGroupLoading.value = false;
  }
};

defineExpose({ openBatchGroupDialog });
</script>
