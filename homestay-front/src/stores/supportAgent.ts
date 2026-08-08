import { defineStore } from "pinia";
import { ref } from "vue";

export const useSupportAgentStore = defineStore("supportAgent", () => {
  const dialogVisible = ref(false);
  const contextHomestayId = ref<number | null>(null);
  const contextOrderId = ref<number | null>(null);

  const openAgentDialog = (homestayId?: number | null, orderId?: number | null) => {
    contextHomestayId.value = homestayId ?? null;
    contextOrderId.value = orderId ?? null;
    dialogVisible.value = true;
  };

  const closeAgentDialog = () => {
    dialogVisible.value = false;
    contextHomestayId.value = null;
    contextOrderId.value = null;
  };

  return {
    dialogVisible,
    contextHomestayId,
    contextOrderId,
    openAgentDialog,
    closeAgentDialog,
  };
});
