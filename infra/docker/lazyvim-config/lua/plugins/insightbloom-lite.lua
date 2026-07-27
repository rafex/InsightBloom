-- Alcance "liviano/esencial" pedido para la imagen -lazyvim: nucleo de LazyVim (lazy.nvim +
-- LazyVim + snacks.nvim) con el picker/explorer default de v16 (fzf-lua + neo-tree), sin
-- bufferline/noice/trouble/dashboard. 100% offline: blink.cmp fuerza implementacion Lua pura
-- (su default intenta bajar un binario nativo de GitHub Releases la primera vez que se usa).
--
-- IMPORTANTE: nui.nvim y mini.icons NO se deshabilitan pese a no ser plugins de UI "grandes" --
-- neo-tree.nvim depende de nui.nvim en runtime (aunque no lo declara como dependencia estatica
-- en su spec; confirmado con un fallo real -- "no file nui/line.lua" -- al abrir el explorador
-- sin vendorizarlo) y de un provider de iconos (mini.icons). Deshabilitarlos rompe el explorador
-- de archivos por completo.
return {
  { "folke/trouble.nvim", enabled = false },
  { "akinsho/bufferline.nvim", enabled = false },
  { "folke/noice.nvim", enabled = false },
  {
    "folke/snacks.nvim",
    opts = {
      dashboard = { enabled = false },
    },
  },
  {
    "saghen/blink.cmp",
    opts = {
      fuzzy = {
        implementation = "lua",
        prebuilt_binaries = { download = false },
      },
    },
  },
}
