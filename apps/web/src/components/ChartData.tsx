import { formatNumber } from "@/lib/format";

/** Keyboard-accessible values accompany charts without depending on color or hover. */
export function ChartData({ caption, columns, rows }: { caption: string; columns: string[]; rows: (string | number)[][] }) {
  return <details className="mt-3 text-xs text-slate-600">
    <summary className="cursor-pointer font-medium">View chart data</summary>
    {rows.length ? <div className="mt-2 overflow-x-auto"><table className="data-table w-full">
      <caption className="sr-only">{caption}</caption>
      <thead><tr>{columns.map((column) => <th key={column} scope="col">{column}</th>)}</tr></thead>
      <tbody>{rows.map((row, index) => <tr key={index}>{row.map((value, cell) => <td key={cell}>{typeof value === "number" ? formatNumber(value) : value}</td>)}</tr>)}</tbody>
    </table></div> : <p className="mt-2">No data available for this chart.</p>}
  </details>;
}
