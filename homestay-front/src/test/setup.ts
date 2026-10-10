import { config } from "@vue/test-utils";
import { afterEach, vi } from "vitest";
import { defineComponent, h } from "vue";

const ElButton = defineComponent({
  name: "ElButton",
  inheritAttrs: false,
  props: {
    loading: {
      type: Boolean,
      default: false,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    size: {
      type: String,
      default: "",
    },
    text: {
      type: Boolean,
      default: false,
    },
  },
  emits: ["click"],
  template: `
    <button
      v-bind="$attrs"
      type="button"
      :disabled="loading || disabled"
      @click="$emit('click', $event)"
    >
      <slot />
    </button>
  `,
});

const ElInput = defineComponent({
  name: "ElInput",
  inheritAttrs: false,
  props: {
    modelValue: {
      type: [String, Number],
      default: "",
    },
    placeholder: {
      type: String,
      default: "",
    },
  },
  emits: ["update:modelValue", "keyup"],
  template: `
    <input
      :value="modelValue ?? ''"
      :placeholder="placeholder"
      @input="$emit('update:modelValue', $event.target.value)"
      @keyup="$emit('keyup', $event)"
    />
  `,
});

const ElAutocomplete = defineComponent({
  name: "ElAutocomplete",
  inheritAttrs: false,
  props: {
    modelValue: {
      type: [String, Number],
      default: "",
    },
    placeholder: {
      type: String,
      default: "",
    },
    fetchSuggestions: {
      type: Function,
      default: undefined,
    },
  },
  emits: ["update:modelValue", "input", "keyup", "clear", "select"],
  template: `
    <input
      :value="modelValue ?? ''"
      :placeholder="placeholder"
      @input="
        $emit('update:modelValue', $event.target.value);
        $emit('input', $event.target.value);
        if ($event.target.value === '') $emit('clear');
      "
      @keyup="$emit('keyup', $event)"
    />
  `,
});

const ElInputNumber = defineComponent({
  name: "ElInputNumber",
  inheritAttrs: false,
  props: {
    modelValue: {
      type: Number,
      default: undefined,
    },
    placeholder: {
      type: String,
      default: "",
    },
    min: {
      type: Number,
      default: undefined,
    },
    max: {
      type: Number,
      default: undefined,
    },
    size: {
      type: String,
      default: "",
    },
  },
  emits: ["update:modelValue", "change"],
  template: `
    <input
      type="number"
      :value="modelValue ?? ''"
      :placeholder="placeholder"
      :min="min"
      :max="max"
      @input="$emit('update:modelValue', $event.target.value === '' ? undefined : Number($event.target.value))"
      @change="$emit('change', $event.target.value === '' ? undefined : Number($event.target.value))"
    />
  `,
});

const ElSlider = defineComponent({
  name: "ElSlider",
  inheritAttrs: false,
  props: {
    modelValue: {
      type: Number,
      default: 0,
    },
    min: {
      type: Number,
      default: 0,
    },
    max: {
      type: Number,
      default: 100,
    },
    step: {
      type: Number,
      default: 1,
    },
  },
  emits: ["update:modelValue", "change"],
  template: `
    <input
      type="range"
      :value="modelValue"
      :min="min"
      :max="max"
      :step="step"
      @input="$emit('update:modelValue', Number($event.target.value))"
      @change="$emit('change', Number($event.target.value))"
    />
  `,
});

const ElDatePicker = defineComponent({
  name: "ElDatePicker",
  inheritAttrs: false,
  props: {
    modelValue: {
      type: String,
      default: "",
    },
    placeholder: {
      type: String,
      default: "",
    },
    size: {
      type: String,
      default: "",
    },
  },
  emits: ["update:modelValue", "change"],
  template: `
    <input
      type="date"
      :value="modelValue ?? ''"
      :placeholder="placeholder"
      @input="$emit('update:modelValue', $event.target.value || undefined)"
      @change="$emit('change', $event.target.value || null)"
    />
  `,
});

const ElSwitch = defineComponent({
  name: "ElSwitch",
  inheritAttrs: false,
  props: {
    modelValue: {
      type: Boolean,
      default: false,
    },
  },
  emits: ["update:modelValue", "change"],
  template: `
    <input
      type="checkbox"
      :checked="modelValue"
      @change="$emit('update:modelValue', $event.target.checked); $emit('change', $event.target.checked)"
    />
  `,
});

const ElCascader = defineComponent({
  name: "ElCascader",
  inheritAttrs: false,
  props: {
    modelValue: {
      type: Array,
      default: () => [],
    },
    options: {
      type: Array,
      default: () => [],
    },
    placeholder: {
      type: String,
      default: "",
    },
    size: {
      type: String,
      default: "",
    },
  },
  emits: ["update:modelValue", "change"],
  template: `<div data-testid="el-cascader"><slot /></div>`,
});

const ElTooltip = defineComponent({
  name: "ElTooltip",
  inheritAttrs: false,
  props: {
    content: {
      type: String,
      default: "",
    },
    placement: {
      type: String,
      default: "",
    },
  },
  setup(_, { slots }) {
    return () => slots.default?.();
  },
});

const ElPopover = defineComponent({
  name: "ElPopover",
  inheritAttrs: false,
  emits: ["hide"],
  setup(_, { slots }) {
    return () => h("div", { class: "el-popover-stub" }, [slots.reference?.(), slots.default?.()]);
  },
});

const ElDrawer = defineComponent({
  name: "ElDrawer",
  inheritAttrs: false,
  props: {
    modelValue: {
      type: Boolean,
      default: false,
    },
  },
  emits: ["update:modelValue"],
  setup(_, { slots }) {
    return () => h("div", { class: "el-drawer-stub" }, slots.default?.());
  },
});

const ElIcon = defineComponent({
  name: "ElIcon",
  inheritAttrs: false,
  setup(_, { slots }) {
    return () => h("span", { class: "el-icon-stub" }, slots.default?.());
  },
});

config.global.components = {
  "el-button": ElButton,
  "el-input": ElInput,
  "el-autocomplete": ElAutocomplete,
  "el-input-number": ElInputNumber,
  "el-slider": ElSlider,
  "el-date-picker": ElDatePicker,
  "el-switch": ElSwitch,
  "el-cascader": ElCascader,
  "el-tooltip": ElTooltip,
  "el-popover": ElPopover,
  "el-drawer": ElDrawer,
  "el-icon": ElIcon,
};

config.global.stubs = {
  transition: false,
  teleport: true,
};

Object.defineProperty(window, "scrollTo", {
  writable: true,
  value: vi.fn(),
});

Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
  writable: true,
  value: vi.fn(),
});

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

vi.stubGlobal("ResizeObserver", ResizeObserverMock);

afterEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  localStorage.clear();
});
