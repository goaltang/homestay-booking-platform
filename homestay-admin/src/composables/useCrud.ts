import { ref, reactive, type Ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import type { FormInstance, FormRules } from "element-plus";
import { normalizePageResponse, unwrapApiData } from "@/api/response";

/** 从响应中解包 data（适配 axios 包装和直接返回两种格式） */
export function unwrapData<T>(res: any): T {
  return unwrapApiData<T>(res);
}

export interface Pagination {
  page: number;
  size: number;
  total: number;
}

export interface UseCrudOptions<
  T extends Record<string, any>,
  Q extends Record<string, any> = Record<string, any>,
> {
  /** 列表查询 API */
  listApi: (query: Q & { page?: number; size?: number }) => Promise<any>;
  /** 新增 API */
  createApi?: (data: any) => Promise<any>;
  /** 更新 API */
  updateApi?: (id: any, data: any) => Promise<any>;
  /** 删除 API */
  deleteApi?: (id: any) => Promise<any>;
  /** 默认查询参数 */
  defaultQuery?: Q;
  /** 默认表单数据 */
  defaultForm?: Partial<T>;
  /** 表单校验规则 */
  rules?: FormRules;
  /** 是否需要分页（默认 true） */
  pagination?: boolean;
  /** 提交成功后回调 */
  afterSubmit?: () => void;
  /** 删除前确认文案生成 */
  deleteConfirmText?: (row: T) => string;
  /** 主键字段名（默认 id） */
  idKey?: keyof T;
}

export interface UseCrudReturn<T extends Record<string, any>, Q extends Record<string, any>> {
  // 状态
  loading: ReturnType<typeof ref<boolean>>;
  tableData: Ref<T[]>;
  query: Q & Record<string, any>;
  pagination: Pagination;
  dialogVisible: ReturnType<typeof ref<boolean>>;
  editMode: ReturnType<typeof ref<boolean>>;
  formRef: ReturnType<typeof ref<FormInstance | undefined>>;
  form: Partial<T> & Record<string, any>;
  currentId: ReturnType<typeof ref<number | string | null>>;
  rules: FormRules;

  // 方法
  getList: () => Promise<void>;
  handleAdd: () => void;
  handleEdit: (row: T) => void;
  handleDelete: (row: T) => Promise<void>;
  handleSubmit: () => Promise<void>;
  resetForm: () => void;
  handlePageChange: (page: number) => void;
  handleSizeChange: (size: number) => void;
  setForm: (data: Partial<T>) => void;
}

/**
 * 通用 CRUD 组合式函数
 *
 * @example
 * const {
 *   loading, tableData, query, pagination,
 *   dialogVisible, editMode, formRef, form, currentId,
 *   getList, handleAdd, handleEdit, handleDelete, handleSubmit
 * } = useCrud({
 *   listApi: getPricingRules,
 *   createApi: createPricingRule,
 *   updateApi: updatePricingRule,
 *   deleteApi: deletePricingRule,
 *   defaultQuery: { page: 0, size: 20, ruleType: '' },
 *   defaultForm: { name: '', scopeType: 'GLOBAL', ruleType: 'WEEKEND', ... },
 *   rules: { name: [{ required: true, message: '请输入名称' }] },
 * })
 */
export function useCrud<
  T extends Record<string, any>,
  Q extends Record<string, any> = Record<string, any>,
>(options: UseCrudOptions<T, Q>): UseCrudReturn<T, Q> {
  const {
    listApi,
    createApi,
    updateApi,
    deleteApi,
    defaultQuery = {} as Q,
    defaultForm = {} as Partial<T>,
    rules = {},
    pagination: enablePagination = true,
    afterSubmit,
    deleteConfirmText = (row: T) => `确定删除「${row.name || row.id || "该记录"}」吗？`,
    idKey = "id",
  } = options;

  // 列表状态
  const loading = ref(false);
  const tableData = ref<T[]>([]) as Ref<T[]>;
  const query = reactive({ ...defaultQuery }) as any;
  const pagination = reactive<Pagination>({
    page: (defaultQuery as any).page ?? 0,
    size: (defaultQuery as any).size ?? 20,
    total: 0,
  });

  // 表单状态
  const dialogVisible = ref(false);
  const editMode = ref(false);
  const formRef = ref<FormInstance>();
  const form = reactive({ ...defaultForm }) as any;
  const currentId = ref<number | string | null>(null);

  /** 获取列表 */
  const getList = async () => {
    loading.value = true;
    try {
      const params = enablePagination
        ? { ...query, page: pagination.page, size: pagination.size }
        : { ...query };
      const res = await listApi(params as Q & { page?: number; size?: number });
      const pageData = normalizePageResponse<T>(res);
      tableData.value = pageData.list;
      pagination.total = pageData.total;
    } catch (e) {
      console.error("获取列表失败:", e);
      tableData.value = [];
    } finally {
      loading.value = false;
    }
  };

  /** 重置表单 */
  const resetForm = () => {
    Object.keys(form).forEach((key) => {
      delete (form as any)[key];
    });
    Object.assign(form, defaultForm);
  };

  /** 设置表单数据 */
  const setForm = (data: Partial<T>) => {
    Object.assign(form, data);
  };

  /** 新增 */
  const handleAdd = () => {
    editMode.value = false;
    currentId.value = null;
    resetForm();
    dialogVisible.value = true;
  };

  /** 编辑 */
  const handleEdit = (row: T) => {
    editMode.value = true;
    currentId.value = row[idKey] as number | string;
    resetForm();
    Object.assign(form, row);
    dialogVisible.value = true;
  };

  /** 删除 */
  const handleDelete = async (row: T) => {
    if (!deleteApi) {
      ElMessage.warning("未配置删除接口");
      return;
    }
    try {
      await ElMessageBox.confirm(deleteConfirmText(row), "提示", { type: "warning" });
      await deleteApi(row[idKey] as number | string);
      ElMessage.success("删除成功");
      await getList();
    } catch (e: any) {
      if (e !== "cancel") {
        ElMessage.error(e?.response?.data || "删除失败");
      }
    }
  };

  /** 提交 */
  const handleSubmit = async () => {
    if (!formRef.value) return;
    await formRef.value.validate(async (valid) => {
      if (!valid) return;
      try {
        if (editMode.value && currentId.value != null && updateApi) {
          await updateApi(currentId.value, { ...form });
          ElMessage.success("更新成功");
        } else if (createApi) {
          await createApi({ ...form });
          ElMessage.success("创建成功");
        } else {
          ElMessage.warning("未配置提交接口");
          return;
        }
        dialogVisible.value = false;
        await getList();
        afterSubmit?.();
      } catch (e: any) {
        ElMessage.error(e?.response?.data || "操作失败");
      }
    });
  };

  /** 页码变化 */
  const handlePageChange = (page: number) => {
    pagination.page = page;
    getList();
  };

  /** 每页条数变化 */
  const handleSizeChange = (size: number) => {
    pagination.size = size;
    pagination.page = 0;
    getList();
  };

  return {
    loading,
    tableData,
    query,
    pagination,
    dialogVisible,
    editMode,
    formRef,
    form,
    currentId,
    rules,
    getList,
    handleAdd,
    handleEdit,
    handleDelete,
    handleSubmit,
    resetForm,
    handlePageChange,
    handleSizeChange,
    setForm,
  };
}

export default useCrud;
