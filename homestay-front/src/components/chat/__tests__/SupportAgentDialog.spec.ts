import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, h, ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";

// ---------- mocks ----------
const chatWithAgentMock = vi.fn();
const confirmAgentActionMock = vi.fn();

vi.mock("@/api/supportAgent", () => ({
  chatWithAgent: (...args: unknown[]) => chatWithAgentMock(...args),
  confirmAgentAction: (...args: unknown[]) => confirmAgentActionMock(...args),
}));

const elMessageError = vi.fn();
vi.mock("element-plus", () => ({
  ElMessage: { error: elMessageError },
}));

// store 状态控制（真实 pinia + 可控 store 方法）
let dialogVisible = ref(false);
let contextOrderId = ref<number | null>(null);
let contextHomestayId = ref<number | null>(null);

vi.mock("@/stores/supportAgent", () => ({
  useSupportAgentStore: () => ({
    dialogVisible,
    contextOrderId,
    contextHomestayId,
    openAgentDialog: vi.fn(),
    closeAgentDialog: vi.fn(),
  }),
}));

const SupportAgentDialog = (await import("@/components/chat/SupportAgentDialog.vue")).default;

// ---------- stubs（setup.ts 未覆盖的 Element Plus 组件，用 h() 渲染避免 JSX） ----------
const ElDialogStub = defineComponent({
  name: "ElDialog",
  props: { modelValue: Boolean, title: String, width: String },
  emits: ["update:modelValue"],
  setup(_, { slots, attrs }) {
    return () =>
      h("div", { class: "el-dialog-stub", "data-open": String(attrs.modelValue ?? false) }, [
        slots.default?.(),
      ]);
  },
});

const ElAlertStub = defineComponent({
  name: "ElAlert",
  props: { title: String, type: String, closable: Boolean, "show-icon": Boolean },
  setup(props, { slots }) {
    return () => h("div", { class: "el-alert-stub" }, [props.title ?? slots.default?.()]);
  },
});

const ElTagStub = defineComponent({
  name: "ElTag",
  props: { size: String, type: String },
  setup(_, { slots }) {
    return () => h("span", { class: "el-tag-stub" }, [slots.default?.()]);
  },
});

const ElTextareaStub = defineComponent({
  name: "ElTextarea",
  props: { modelValue: String, rows: Number, disabled: Boolean, placeholder: String },
  emits: ["update:modelValue", "keydown"],
  setup(_, { attrs, emit }) {
    return () =>
      h("textarea", {
        "data-testid": "agent-input",
        value: attrs.modelValue ?? "",
        placeholder: (attrs.placeholder as string) ?? "",
        disabled: Boolean(attrs.disabled),
        onInput: (e: Event) => emit("update:modelValue", (e.target as HTMLTextAreaElement).value),
        onKeydown: (e: KeyboardEvent) => emit("keydown", e),
      });
  },
});

// ---------- helpers ----------
interface PendingAction {
  action: string;
  orderId: number;
  reason: string;
  summary: string;
}

const makePendingAction = (overrides: Partial<PendingAction> = {}): PendingAction => ({
  action: "request_user_refund",
  orderId: 1001,
  reason: "行程有变",
  summary: "将为您申请退款（订单 HK20260001）",
  ...overrides,
});

const mountDialog = async () => {
  dialogVisible.value = true;
  const wrapper = mount(SupportAgentDialog, {
    global: {
      stubs: {
        ElDialog: ElDialogStub,
        ElAlert: ElAlertStub,
        ElTag: ElTagStub,
        ElTextarea: ElTextareaStub,
      },
    },
  });
  await flushPromises();
  return wrapper;
};

const typeQuestion = async (wrapper: VueWrapper, text: string) => {
  // 组件模板用 el-input type="textarea"，setup.ts 的 ElInput stub 渲染为普通 <input>
  // ElButton stub 渲染 <button> 无 el-button class；键盘触发不可靠（stub 只转发 keyup），直接点按钮
  const input = wrapper.find(".message-input input");
  await input.setValue(text);
  await wrapper.find(".message-input button").trigger("click");
  await flushPromises();
};

describe("SupportAgentDialog 确认卡片", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    dialogVisible = ref(false);
    contextOrderId = ref(null);
    contextHomestayId = ref(null);
    chatWithAgentMock.mockReset();
    confirmAgentActionMock.mockReset();
    elMessageError.mockReset();
  });

  it("agent 回复带 pendingAction 时渲染确认卡片（摘要+两个按钮）", async () => {
    chatWithAgentMock.mockResolvedValue({
      answer: "已为您准备好退款申请，请确认。",
      handoffToHuman: false,
      toolUsed: "request_user_refund",
      conversationId: "conv-1",
      pendingAction: makePendingAction(),
    });

    const wrapper = await mountDialog();
    await typeQuestion(wrapper, "帮我申请退款");

    expect(chatWithAgentMock).toHaveBeenCalledTimes(1);
    const card = wrapper.find(".action-card");
    expect(card.exists()).toBe(true);
    expect(card.text()).toContain("待确认操作");
    expect(card.text()).toContain("将为您申请退款（订单 HK20260001）");
    expect(wrapper.text()).toContain("确认执行");
    expect(wrapper.text()).toContain("取消");
  });

  it("无 pendingAction 的普通回复不渲染卡片", async () => {
    chatWithAgentMock.mockResolvedValue({
      answer: "您的订单退款政策如下：入住前 7 天可全额退款。",
      handoffToHuman: false,
      toolUsed: null,
      conversationId: "conv-1",
      pendingAction: null,
    });

    const wrapper = await mountDialog();
    await typeQuestion(wrapper, "退款政策是什么");

    expect(wrapper.find(".action-card").exists()).toBe(false);
    expect(wrapper.text()).toContain("您的订单退款政策如下");
  });

  it("点击确认调用 confirmAgentAction 并追加结果消息、卡片消失", async () => {
    chatWithAgentMock.mockResolvedValue({
      answer: "已为您准备好退款申请，请确认。",
      handoffToHuman: false,
      toolUsed: "request_user_refund",
      conversationId: "conv-1",
      pendingAction: makePendingAction(),
    });
    confirmAgentActionMock.mockResolvedValue({
      answer: "已为订单 HK20260001 提交退款申请，等待房东审批。",
      handoffToHuman: false,
      toolUsed: null,
      conversationId: "conv-2",
      pendingAction: null,
    });

    const wrapper = await mountDialog();
    await typeQuestion(wrapper, "帮我申请退款");

    await wrapper.find(".action-card-buttons button").trigger("click");
    await flushPromises();

    expect(confirmAgentActionMock).toHaveBeenCalledTimes(1);
    expect(confirmAgentActionMock).toHaveBeenCalledWith(makePendingAction());
    expect(wrapper.find(".action-card").exists()).toBe(false);
    expect(wrapper.text()).toContain("已为订单 HK20260001 提交退款申请");
  });

  it("点击取消移除卡片并追加取消提示，不调用 confirm", async () => {
    chatWithAgentMock.mockResolvedValue({
      answer: "已为您准备好退款申请，请确认。",
      handoffToHuman: false,
      toolUsed: "request_user_refund",
      conversationId: "conv-1",
      pendingAction: makePendingAction(),
    });

    const wrapper = await mountDialog();
    await typeQuestion(wrapper, "帮我申请退款");

    const buttons = wrapper.findAll(".action-card-buttons button");
    await buttons[1].trigger("click"); // 取消按钮
    await flushPromises();

    expect(confirmAgentActionMock).not.toHaveBeenCalled();
    expect(wrapper.find(".action-card").exists()).toBe(false);
    expect(wrapper.text()).toContain("已取消该操作");
  });

  it("confirm 失败时保留卡片并提示错误", async () => {
    chatWithAgentMock.mockResolvedValue({
      answer: "已为您准备好退款申请，请确认。",
      handoffToHuman: false,
      toolUsed: "request_user_refund",
      conversationId: "conv-1",
      pendingAction: makePendingAction(),
    });
    confirmAgentActionMock.mockRejectedValue(new Error("network"));

    const wrapper = await mountDialog();
    await typeQuestion(wrapper, "帮我申请退款");

    await wrapper.find(".action-card-buttons button").trigger("click");
    await flushPromises();

    expect(elMessageError).toHaveBeenCalled();
    expect(wrapper.find(".action-card").exists()).toBe(true); // 卡片保留
  });
});
