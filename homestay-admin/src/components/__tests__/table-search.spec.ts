import { afterEach, describe, expect, it, vi } from "vitest";
import { mount, type VueWrapper } from "@vue/test-utils";
import { defineComponent, h, nextTick, reactive } from "vue";
import ElementPlus from "element-plus";
import TableSearch from "../table-search.vue";
import type { FormOptionList, FormOptionType } from "@/types/form-option";

const wrappers: VueWrapper[] = [];
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
});

function render(query: Record<string, unknown>, options: FormOptionList[]) {
  const wrapper = mount(TableSearch, {
    props: { query, options },
    global: { plugins: [ElementPlus] },
  });
  wrappers.push(wrapper);
  return wrapper;
}

describe("查询组件的数据流", () => {
  const controls: [FormOptionType, string, unknown][] = [
    ["input", "ElInput", "关键字"],
    ["select", "ElSelect", "active"],
    ["remote-select", "ElSelect", 42],
    ["date", "ElDatePicker", "2026-10-10"],
    ["datetime", "ElDatePicker", "2026-10-10 10:00:00"],
    ["daterange", "ElDatePicker", ["2026-10-10", "2026-10-12"]],
    ["datetimerange", "ElDatePicker", ["2026-10-10 10:00:00", "2026-10-12 10:00:00"]],
    ["number", "ElInputNumber", 0],
    ["switch", "ElSwitch", false],
    ["cascader", "ElCascader", ["province", "city"]],
  ];

  it.each(controls)("%s 控件通过事件更新，不修改传入对象", async (type, name, value) => {
    const query = Object.freeze({ filter: undefined, page: 3 });
    const wrapper = render(query, [{ type, prop: "filter", label: "筛选" }]);
    wrapper.findComponent({ name }).vm.$emit("update:modelValue", value);
    await nextTick();
    const updated = wrapper.emitted("update:query")?.[0]?.[0];
    expect(updated).toEqual({ filter: value, page: 3 });
    expect(updated).not.toBe(query);
    expect(query.filter).toBeUndefined();
  });

  it("数字范围更新保留另一端，不修改传入数组", async () => {
    const range = Object.freeze([100, 500]);
    const query = Object.freeze({ price: range });
    const wrapper = render(query, [{ type: "number-range", prop: "price", label: "价格" }]);
    const inputs = wrapper.findAll("input");
    await inputs[0].setValue("200");
    const first = wrapper.emitted("update:query")?.at(-1)?.[0] as Record<string, unknown>;
    expect(first.price).toEqual([200, 500]);
    expect(first.price).not.toBe(range);
    await wrapper.setProps({ query: first });
    await inputs[1].setValue("800");
    expect(wrapper.emitted("update:query")?.at(-1)?.[0]).toEqual({ price: [200, 800] });
    expect(range).toEqual([100, 500]);
  });

  it("空数字范围可以输入", async () => {
    const wrapper = render({}, [{ type: "number-range", prop: "price", label: "价格" }]);
    await wrapper.findAll("input")[1].setValue("800");
    expect(wrapper.emitted("update:query")?.at(-1)?.[0]).toEqual({ price: [null, 800] });
  });

  it("父页面接收输入，搜索读取新值，重置后再搜索且保留非筛选字段", async () => {
    const query = reactive({ keyword: "默认值", price: [100, 500], page: 3 });
    const options: FormOptionList[] = [
      { type: "input", prop: "keyword", label: "关键字" },
      { type: "number-range", prop: "price", label: "价格" },
    ];
    const search = vi.fn(() => ({ ...query, price: [...query.price] }));
    const Parent = defineComponent({
      setup: () => () => h(TableSearch, {
        query, options, search,
        "onUpdate:query": (value: Record<string, unknown>) => Object.assign(query, value),
      }),
    });
    const wrapper = mount(Parent, { global: { plugins: [ElementPlus] } });
    wrappers.push(wrapper);
    await wrapper.find('input[placeholder="请输入关键字"]').setValue("新的关键字");
    expect(query.keyword).toBe("新的关键字");
    const button = (label: string) => wrapper.findAll("button").find((item) => item.text() === label)!;
    await button("搜索").trigger("click");
    expect(search.mock.results.at(-1)?.value.keyword).toBe("新的关键字");
    await button("重置").trigger("click");
    expect(query).toEqual({ keyword: "默认值", price: [null, null], page: 3 });
    expect(search.mock.results.at(-1)?.value).toEqual(query);
    expect(search).toHaveBeenCalledTimes(2);
    query.keyword = "父页面更新";
    await nextTick();
    expect((wrapper.find('input[placeholder="请输入关键字"]').element as HTMLInputElement).value)
      .toBe("父页面更新");
  });
});
