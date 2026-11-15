import { describe, expect, it } from "vitest";
import { resolveDemoAccounts } from "../demoAccounts";

describe("resolveDemoAccounts", () => {
  it("未配置时不开启演示模式", () => {
    expect(resolveDemoAccounts({})).toEqual([]);
    expect(resolveDemoAccounts({ VITE_DEMO_GUEST_USERNAME: "  " })).toEqual([]);
  });

  it("按配置生成房客和房东账号，默认使用演示快照密码", () => {
    const accounts = resolveDemoAccounts({
      VITE_DEMO_GUEST_USERNAME: " user_4424 ",
      VITE_DEMO_HOST_USERNAME: "host_0097",
    });
    expect(accounts.map((a) => [a.role, a.username, a.password])).toEqual([
      ["guest", "user_4424", "123456"],
      ["host", "host_0097", "123456"],
    ]);
  });

  it("只配置房东时只给房东入口，并允许覆盖密码", () => {
    const accounts = resolveDemoAccounts({
      VITE_DEMO_HOST_USERNAME: "host_0097",
      VITE_DEMO_PASSWORD: "abc",
    });
    expect(accounts).toHaveLength(1);
    expect(accounts[0]).toMatchObject({ role: "host", password: "abc" });
  });
});
