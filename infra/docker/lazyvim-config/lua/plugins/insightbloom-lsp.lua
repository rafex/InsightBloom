-- Paridad de LSP con la imagen "neovim" actual (Dockerfile.code-ide-neovim / nvim-init.lua):
-- mismos 5 lenguajes, apuntando a los MISMOS binarios pre-instalados globalmente en la imagen
-- (typescript-language-server / pyright-langserver / vscode-*-language-server / jdtls).
--
-- mason = false: LazyVim normalmente delega en mason-lspconfig para instalar el binario del
-- servidor si falta. Como ya viene preinstalado y el pod puede tener internet_enabled=false, se
-- desactiva ese puente para que nvim-lspconfig use el binario del PATH directamente, sin tocar
-- red. mason.nvim/mason-lspconfig.nvim quedan vendorizados (LazyVim los trae por defecto) pero
-- deshabilitados: nunca se cargan, nunca consultan su registro remoto.
return {
  {
    "neovim/nvim-lspconfig",
    opts = {
      mason = false,
      servers = {
        ts_ls = {
          filetypes = { "javascript", "javascriptreact", "typescript", "typescriptreact", "json", "jsonc" },
        },
        pyright = {},
        html = {},
        cssls = {},
      },
    },
  },
  { "mason.nvim", enabled = false },
  { "mason-lspconfig.nvim", enabled = false },
  -- Java (jdtls) via nvim-jdtls, igual que la imagen actual -- nvim-lspconfig no tiene un
  -- servidor "jdtls" utilizable sin este plugin (necesita workspace por proyecto).
  {
    "mfussenegger/nvim-jdtls",
    ft = "java",
    config = function()
      vim.api.nvim_create_autocmd("FileType", {
        pattern = "java",
        callback = function()
          local root_dir = require("jdtls.setup").find_root({ ".git", "mvnw", "gradlew", "pom.xml", "build.gradle" })
            or vim.fn.getcwd()
          local workspace_dir = vim.fn.stdpath("cache") .. "/jdtls-workspace/" .. vim.fn.fnamemodify(root_dir, ":p:h:t")
          require("jdtls").start_or_attach({
            cmd = { "jdtls", "-data", workspace_dir },
            root_dir = root_dir,
          })
        end,
      })
    end,
  },
  -- Parsers de tree-sitter adicionales a los defaults de LazyVim, para mantener paridad con la
  -- imagen actual (java/css). Todos, incluidos los defaults, se compilan en build time (ver
  -- Dockerfile) -- ninguno se descarga/compila en runtime.
  {
    "nvim-treesitter/nvim-treesitter",
    opts = {
      ensure_installed = { "java", "css" },
    },
  },
}
