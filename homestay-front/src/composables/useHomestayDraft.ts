/**
 * 房源表单草稿逻辑（保存草稿 / 自动保存 / 加载草稿）
 * 从 HomestayForm.vue 抽取
 */
import { ref, type ComputedRef, type Ref } from "vue";
import { ElMessage } from "element-plus";
import { saveHomestayDraft } from "@/api/homestay";
import { calculateFormCompletion } from "@/utils/homestayForm";

const DRAFT_STORAGE_KEY = "homestayDraft";

interface DraftOptions {
  form: Record<string, any>;
  isEdit: ComputedRef<boolean>;
  selectedAreaCodes: Ref<string[]>;
  preprocess: () => Record<string, any>;
  router: { replace: (path: string) => void };
}

export function useHomestayDraft(options: DraftOptions) {
  const { form, isEdit, selectedAreaCodes, preprocess, router } = options;

  const savingDraft = ref(false);
  const lastSaved = ref<Date | null>(null);
  const autoSaveInterval = ref<number | null>(null);

  const saveDraft = async () => {
    try {
      savingDraft.value = true;

      const processedData = preprocess();
      const result = await saveHomestayDraft(processedData);

      if (result.data) {
        ElMessage.success("房源草稿保存成功");
        lastSaved.value = new Date();

        // 新建草稿：更新表单 ID/状态并导航到编辑页
        if (!isEdit.value && result.data.id) {
          form.id = result.data.id;
          form.status = "DRAFT";
          router.replace(`/host/homestay/edit/${result.data.id}`);
        }
      } else {
        ElMessage.error(result.message || "保存草稿失败，请稍后重试");
      }
    } catch (error) {
      console.error("保存草稿出错:", error);
    } finally {
      savingDraft.value = false;
    }
  };

  const autoSaveDraft = () => {
    if (calculateFormCompletion(form) === 0) {
      return;
    }
    const draftData = {
      ...form,
      status: "DRAFT",
      _lastSaved: new Date().toISOString(),
    };
    localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(draftData));
    lastSaved.value = new Date();
    console.log("自动保存草稿完成:", new Date().toLocaleTimeString());
  };

  const loadDraft = () => {
    try {
      const draftData = localStorage.getItem(DRAFT_STORAGE_KEY);
      if (draftData) {
        const parsed = JSON.parse(draftData);

        Object.assign(form, parsed);

        // 处理草稿地址回显
        if (parsed.provinceCode && parsed.cityCode) {
          const codes = [parsed.provinceCode, parsed.cityCode];
          if (parsed.districtCode) {
            codes.push(parsed.districtCode);
          }
          selectedAreaCodes.value = codes;
        } else {
          selectedAreaCodes.value = [];
        }

        lastSaved.value = new Date(parsed._lastSaved || Date.now());
        ElMessage.success("已恢复上次编辑的草稿");
      } else {
        form.status = "DRAFT";
        form.maxGuests = 1;
        form.minNights = 1;
      }
    } catch (e) {
      console.error("加载草稿失败:", e);
      ElMessage.warning("加载草稿失败，将使用默认值");
    }
  };

  const startAutoSave = () => {
    autoSaveInterval.value = window.setInterval(autoSaveDraft, 3 * 60 * 1000);
  };

  const stopAutoSave = () => {
    if (autoSaveInterval.value) {
      clearInterval(autoSaveInterval.value);
      autoSaveInterval.value = null;
    }
  };

  return {
    savingDraft,
    lastSaved,
    autoSaveInterval,
    saveDraft,
    autoSaveDraft,
    loadDraft,
    startAutoSave,
    stopAutoSave,
  };
}
