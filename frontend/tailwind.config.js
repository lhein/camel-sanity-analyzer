/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        status: {
          healthy: "#22c55e",
          outdated: "#f59e0b",
          warning: "#eab308",
          critical: "#ef4444",
          unknown: "#64748b",
        },
      },
    },
  },
  plugins: [],
};
