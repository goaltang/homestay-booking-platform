/**
 * Creates a debounced version of the provided function.
 * The debounced function will delay invoking fn until after wait milliseconds
 * have elapsed since the last time the debounced function was invoked.
 *
 * @param fn - The function to debounce
 * @param wait - The number of milliseconds to delay (default: 300ms)
 * @returns A debounced version of the function
 */
export function debounce<T extends (...args: any[]) => any>(
  fn: T,
  wait: number = 300
): (...args: Parameters<T>) => void {
  let timeoutId: ReturnType<typeof setTimeout> | null = null;

  return function (this: any, ...args: Parameters<T>) {
    if (timeoutId !== null) {
      clearTimeout(timeoutId);
    }

    timeoutId = setTimeout(() => {
      fn.apply(this, args);
      timeoutId = null;
    }, wait);
  };
}

/**
 * Creates a throttled version of the provided function.
 * The throttled function will invoke fn at most once per every wait milliseconds.
 *
 * @param fn - The function to throttle
 * @param wait - The number of milliseconds to throttle on (default: 300ms)
 * @returns A throttled version of the function
 */
export function throttle<T extends (...args: any[]) => any>(
  fn: T,
  wait: number = 300
): (...args: Parameters<T>) => void {
  let lastTime: number | null = null;
  let timeoutId: ReturnType<typeof setTimeout> | null = null;

  return function (this: any, ...args: Parameters<T>) {
    const now = Date.now();

    if (lastTime === null || now - lastTime >= wait) {
      lastTime = now;
      fn.apply(this, args);
    } else {
      if (timeoutId !== null) {
        clearTimeout(timeoutId);
      }
      timeoutId = setTimeout(
        () => {
          lastTime = Date.now();
          fn.apply(this, args);
          timeoutId = null;
        },
        wait - (now - lastTime)
      );
    }
  };
}
