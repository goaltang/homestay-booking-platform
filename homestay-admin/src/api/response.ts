export interface PageResult<T> {
  list: T[];
  total: number;
  totalPages?: number;
  currentPage?: number;
  page?: number;
  size?: number;
}

type AnyRecord = Record<string, any>;

const isRecord = (value: unknown): value is AnyRecord =>
  value !== null && typeof value === "object" && !Array.isArray(value);

const hasOwn = (value: AnyRecord, key: string) => Object.prototype.hasOwnProperty.call(value, key);

export function unwrapApiData<T>(response: unknown, fallback?: T): T {
  if (isRecord(response) && hasOwn(response, "data") && response.data !== undefined) {
    return response.data as T;
  }

  if (response === undefined || response === null) {
    return fallback as T;
  }

  return response as T;
}

export function normalizeArrayResponse<T>(response: unknown): T[] {
  const payload = unwrapApiData<unknown>(response, []);

  if (Array.isArray(payload)) return payload as T[];

  if (isRecord(payload)) {
    if (Array.isArray(payload.content)) return payload.content as T[];
    if (Array.isArray(payload.list)) return payload.list as T[];
    if (Array.isArray(payload.data)) return payload.data as T[];
  }

  return [];
}

export function normalizePageResponse<T>(
  response: unknown,
  options: { singleObjectAsList?: boolean } = {}
): PageResult<T> {
  const source = isRecord(response) ? response : {};
  const payload = hasOwn(source, "data") && source.data !== undefined ? source.data : response;
  const payloadRecord = isRecord(payload) ? payload : {};

  let list: T[] = [];

  if (Array.isArray(payload)) {
    list = payload as T[];
  } else if (Array.isArray(payloadRecord.content)) {
    list = payloadRecord.content as T[];
  } else if (Array.isArray(payloadRecord.list)) {
    list = payloadRecord.list as T[];
  } else if (Array.isArray(payloadRecord.data)) {
    list = payloadRecord.data as T[];
  } else if (options.singleObjectAsList && isRecord(payload) && Object.keys(payload).length > 0) {
    list = [payload as T];
  }

  const total =
    payloadRecord.totalElements ??
    payloadRecord.total ??
    source.totalElements ??
    source.total ??
    list.length;

  return {
    list,
    total: Number(total) || 0,
    totalPages: payloadRecord.totalPages ?? source.totalPages,
    currentPage:
      payloadRecord.currentPage ?? payloadRecord.page ?? source.currentPage ?? source.page,
    page: payloadRecord.page ?? source.page,
    size: payloadRecord.size ?? source.size,
  };
}
