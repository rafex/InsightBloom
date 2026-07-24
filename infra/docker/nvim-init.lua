-- Config baseada en la imagen "neovim" del IDE (infra/docker/Dockerfile.code-ide-neovim).
-- 100% offline por diseno: el pod puede tener internet_enabled=false (DEC-0023), asi que no
-- hay plugin manager (Lazy/Packer) ni ":TSInstall"/"Mason" en runtime -- todos los plugins y
-- parsers de tree-sitter ya vienen vendorizados/instalados en la imagen (ver Dockerfile).

vim.opt.number = true
vim.opt.termguicolors = true
vim.opt.mouse = "a"
vim.opt.expandtab = true
vim.opt.shiftwidth = 4
vim.opt.tabstop = 4

vim.cmd("syntax on")
vim.cmd("filetype plugin indent on")
vim.cmd("colorscheme habamax") -- viene con Neovim 0.10, no requiere plugin

vim.g.mapleader = " "

-- Syntax highlighting via los parsers de tree-sitter instalados por apk (tree-sitter-java,
-- tree-sitter-python, etc, ver Dockerfile) -- usa el tree-sitter nativo de Neovim 0.10
-- (vim.treesitter), sin el plugin nvim-treesitter (que requiere red/compilador en runtime
-- para instalar parsers via :TSInstall).
local ts_parser_by_filetype = {
  javascriptreact = "javascript",
  typescriptreact = "typescript",
  jsonc = "json",
}
local ts_langs = { "java", "python", "javascript", "javascriptreact", "typescript", "typescriptreact", "json", "jsonc", "html", "css", "bash" }
local ts_parsers = { "java", "python", "javascript", "typescript", "json", "html", "css", "bash" }
for _, lang in ipairs(ts_parsers) do
  local parser_path = "/usr/lib/tree-sitter/" .. lang .. ".so"
  if vim.uv.fs_stat(parser_path) then
    pcall(vim.treesitter.language.add, lang, { path = parser_path })
  end
end
-- Alpine distribuye las consultas de Tree-sitter fuera del runtime de Neovim. La imagen
-- contiene una variante compatible de JavaScript en /etc/insightbloom/queries porque la
-- consulta original usa #is-not?, que Neovim 0.10.4 no conoce.
vim.opt.runtimepath:prepend("/etc/insightbloom")
vim.opt.runtimepath:append("/usr/share/tree-sitter")
vim.api.nvim_create_autocmd("FileType", {
  pattern = ts_langs,
  callback = function(args)
    pcall(vim.treesitter.start, args.buf, ts_parser_by_filetype[vim.bo[args.buf].filetype] or vim.bo[args.buf].filetype)
  end,
})

-- Explorador de archivos (nvim-tree)
require("nvim-tree").setup({})
vim.keymap.set("n", "<leader>e", "<cmd>NvimTreeToggle<cr>", { desc = "Explorer" })

-- Autocompletado (nvim-cmp + LuaSnip + cmp-nvim-lsp)
local cmp = require("cmp")
cmp.setup({
  snippet = {
    expand = function(args)
      require("luasnip").lsp_expand(args.body)
    end,
  },
  mapping = cmp.mapping.preset.insert({
    ["<CR>"] = cmp.mapping.confirm({ select = true }),
    ["<Tab>"] = cmp.mapping.select_next_item(),
    ["<S-Tab>"] = cmp.mapping.select_prev_item(),
  }),
  sources = {
    { name = "nvim_lsp" },
  },
})
local lsp_capabilities = require("cmp_nvim_lsp").default_capabilities()

-- LSP de JavaScript/TypeScript (typescript-language-server, precargado en la imagen).
-- Se habilita tambien para JSON/JSONC para que package.json, jsconfig.json y
-- tsconfig.json tengan diagnosticos y asistencia contextual. El root se detecta
-- por proyecto y single_file_support mantiene util el servidor en archivos sueltos.
local lspconfig_ok, lspconfig = pcall(require, "lspconfig")
if lspconfig_ok then
  local ts_server = lspconfig.ts_ls or lspconfig.tsserver
  if ts_server then
    ts_server.setup({
      cmd = { "typescript-language-server", "--stdio" },
      capabilities = lsp_capabilities,
      filetypes = { "javascript", "javascriptreact", "typescript", "typescriptreact", "json", "jsonc" },
      root_dir = lspconfig.util.root_pattern("package.json", "jsconfig.json", "tsconfig.json", ".git"),
      single_file_support = true,
      init_options = { hostInfo = "neovim" },
      settings = {
        javascript = { inlayHints = { includeInlayParameterNameHints = "all", includeInlayVariableTypeHints = true } },
        typescript = { inlayHints = { includeInlayParameterNameHints = "all", includeInlayVariableTypeHints = true } },
      },
    })
  end

  -- Python: Pyright viene precargado en la imagen; no depende de Mason ni de Internet.
  if lspconfig.pyright then
    lspconfig.pyright.setup({
      cmd = { "pyright-langserver", "--stdio" },
      capabilities = lsp_capabilities,
      filetypes = { "python" },
      root_dir = lspconfig.util.root_pattern("pyproject.toml", "setup.py", "setup.cfg", "requirements.txt", ".git"),
      single_file_support = true,
    })
  end

  -- HTML/CSS: vscode-langservers-extracted aporta los servidores oficiales extraídos de VS Code.
  if lspconfig.html then
    lspconfig.html.setup({
      cmd = { "vscode-html-language-server", "--stdio" },
      capabilities = lsp_capabilities,
      filetypes = { "html" },
      root_dir = lspconfig.util.root_pattern("package.json", ".git"),
      single_file_support = true,
      init_options = { provideFormatter = true },
    })
  end

  if lspconfig.cssls then
    lspconfig.cssls.setup({
      cmd = { "vscode-css-language-server", "--stdio" },
      capabilities = lsp_capabilities,
      filetypes = { "css", "scss", "less" },
      root_dir = lspconfig.util.root_pattern("package.json", ".git"),
      single_file_support = true,
      init_options = { provideFormatter = true },
    })
  end
end

-- LSP de Java (jdtls, instalado via apk -- binario en /usr/bin/jdtls, sin Mason)
vim.api.nvim_create_autocmd("FileType", {
  pattern = "java",
  callback = function()
    local root_dir = require("jdtls.setup").find_root({ ".git", "mvnw", "gradlew", "pom.xml", "build.gradle" })
      or vim.fn.getcwd()
    local workspace_dir = "/home/coder/.cache/jdtls-workspace/" .. vim.fn.fnamemodify(root_dir, ":p:h:t")
    require("jdtls").start_or_attach({
      cmd = { "jdtls", "-data", workspace_dir },
      root_dir = root_dir,
      capabilities = lsp_capabilities,
    })
  end,
})

-- Keymaps LSP basicos (aplican una vez que un language server esta activo en el buffer)
vim.keymap.set("n", "gd", vim.lsp.buf.definition, { desc = "Ir a definicion" })
vim.keymap.set("n", "K", vim.lsp.buf.hover, { desc = "Documentacion" })
vim.keymap.set("n", "<leader>rn", vim.lsp.buf.rename, { desc = "Renombrar" })
vim.keymap.set("n", "<leader>ca", vim.lsp.buf.code_action, { desc = "Code action" })
