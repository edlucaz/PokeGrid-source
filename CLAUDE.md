# CLAUDE.md — pokegrid (pasta `pokegrid`, antes `PokeGrid-source`)

## O que é

App Electron que roda até 4 contas de Poke Idle World lado a lado, cada uma com sessão separada, login salvo e relogin automático. Não automatiza o jogo nem o captcha. Distribuído **só como código-fonte** (o repo público do autor é `soufoka/PokeGrid-source`; o remote `fork` é o do Lucas). README em pt-BR e `README.en.md`.

## Comandos

```bash
npm install
npm start               # electron .
npm run dist            # AppImage (Linux)
npm run dist:win        # portable Windows x64
bash iniciar.sh         # macOS/Linux; Windows: "Abrir PokeGrid.vbs" ou iniciar.bat
```

Não há testes nem lint. Versão atual: ver `package.json` e `CHANGELOG.md` (bilíngue).

## Arquitetura

- `main.js` (processo principal: janelas, tray, sessões, IPC), `preload.js` (ponte mínima) e `index.html` (toda a UI, ~2.600 linhas num arquivo só).
- Credenciais ficam em `accounts.enc`, cifradas por `safeStorage` (DPAPI/keychain) — **nunca versionar**. A janela do app é onde as credenciais vivem: tudo que chega do jogo para a UI deve ser validado/escapado (a correção da v1.5.4 foi exatamente isso).
- `presets/` guarda scripts de extensão injetados nos painéis; `docker/` sobe a versão com Xvfb + VNC/noVNC para rodar em servidor.

## Deploy

Nenhum deploy web. Empacotamento por electron-builder. O setup Docker/VNC foi usado num servidor Hetzner; o entrypoint grava a senha do VNC em texto no volume `/data` (`password-plaintext.txt`, modo 600) — manter fora do git.

## O que nunca vai pro git

- `.env*` (só `.env.example`), `accounts.enc*`, `dom-dump.txt`, `dist/`, `node_modules/`, `*.zip`, `*.log`.
- `backups/` (contém dados de conta de servidor, ex.: tar.gz do Hetzner); o checkout do Lucas já está ignorando isso com uma mudança local ainda não commitada no `.gitignore`.

## Regras do projeto

- Docs em pt-BR; código e termos técnicos em inglês. Commits: Conventional Commits em pt-BR (histórico antigo em minúsculas sem prefixo).
- Pasta `PokeGrid-source` virou `pokegrid` na Fase 2 (2026-11-11) (`_padrao/projetos.json`). O checkout principal tem mudanças não commitadas do Lucas (`index.html`, `main.js`, `preload.js`, ícones, `.gitignore`); esta branch parte do último commit e não as inclui.
- Padrão de todos os projetos: `/mnt/backup500gb/Projetos/_padrao/PADRAO.md` (nível leve).
