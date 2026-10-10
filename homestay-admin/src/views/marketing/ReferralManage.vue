<template>
  <div class="referral-manage-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>邀请裂变配置</span>
        </div>
      </template>

      <el-form :model="form" label-width="140px">
        <el-form-item label="邀请人ID">
          <el-input-number v-model="form.inviterId" :min="1" style="width: 200px" />
        </el-form-item>
        <el-form-item label="被邀请人奖励券">
          <el-select
            v-model="form.templateIdForInvitee"
            placeholder="可选"
            clearable
            style="width: 300px"
          >
            <el-option v-for="t in activeTemplates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="邀请人奖励券">
          <el-select
            v-model="form.templateIdForInviter"
            placeholder="可选"
            clearable
            style="width: 300px"
          >
            <el-option v-for="t in activeTemplates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="最大使用次数">
          <el-input-number v-model="form.maxUses" :min="1" :max="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="有效期（天）">
          <el-input-number v-model="form.validDays" :min="1" :max="365" style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="generateCode" :loading="generating"
            >生成邀请码</el-button
          >
        </el-form-item>
      </el-form>

      <el-divider v-if="generatedCode" />
      <div v-if="generatedCode" class="code-result">
        <el-alert type="success" :closable="false">
          <div>
            邀请码：<strong>{{ generatedCode }}</strong>
          </div>
          <div>过期时间：{{ formatDate(generatedExpireAt) }}</div>
        </el-alert>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import request from "@/utils/request";

const activeTemplates = ref<any[]>([]);
const generating = ref(false);
const generatedCode = ref("");
const generatedExpireAt = ref("");

const form = reactive({
  inviterId: 1,
  templateIdForInvitee: null as number | null,
  templateIdForInviter: null as number | null,
  maxUses: 10,
  validDays: 30,
});

const fetchTemplates = async () => {
  try {
    const res: any = await request({
      url: "/api/admin/promotions/templates",
      method: "get",
      params: { page: 0, size: 100, status: "ACTIVE" },
    });
    const data = res.data || res;
    activeTemplates.value = (data.content || data || []).filter((t: any) => t.status === "ACTIVE");
  } catch (e) {
    console.error("获取模板失败", e);
  }
};

const generateCode = async () => {
  generating.value = true;
  try {
    const res: any = await request({
      url: "/api/admin/promotions/referral-codes",
      method: "post",
      data: {
        inviterId: form.inviterId,
        templateIdForInvitee: form.templateIdForInvitee,
        templateIdForInviter: form.templateIdForInviter,
        maxUses: form.maxUses,
        validDays: form.validDays,
      },
    });
    const data = res.data || res;
    generatedCode.value = data.referralCode;
    generatedExpireAt.value = data.expireAt;
    ElMessage.success("邀请码生成成功");
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.error || "生成失败");
  } finally {
    generating.value = false;
  }
};

const formatDate = (date: string) => {
  if (!date) return "-";
  return new Date(date).toLocaleString();
};

onMounted(fetchTemplates);
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.code-result {
  margin-top: 16px;
}
</style>
