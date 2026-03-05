export const FIXED_DATE = new Date('2024-01-01T00:00:00.000Z');

export function stableStringify(input: unknown): string {
  return JSON.stringify(sortDeep(input), null, 2);
}

function sortDeep(value: any): any {
  if (Array.isArray(value)) return value.map(sortDeep);
  if (value && typeof value === 'object') {
    return Object.keys(value).sort().reduce((acc: any, key) => {
      acc[key] = sortDeep(value[key]);
      return acc;
    }, {});
  }
  return value;
}
