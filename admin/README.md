# DailyWork — configuração inicial do painel

O painel estático fica nesta pasta e usa o Supabase do projeto DailyWork.\n\nURL pública: https://dailywork-w4cw.onrender.com

## Primeiro administrador
1. Crie uma conta de usuário no Supabase em Authentication → Users → Add user.
2. Confirme o e-mail dessa conta.
3. No SQL Editor do Supabase, execute (substitua pelo e-mail que você cadastrou):

```sql
update public.profiles
set role = 'admin'
where email = 'SEU-EMAIL-AQUI';
```

Não coloque senha nem service_role key no HTML, no APK ou no GitHub.

## Publicação no Render
Crie um Static Site conectado ao repositório `nexxtyx7/DailyWork`, branch `main`.
- Build command: `echo "DailyWork admin ready"`
- Publish directory: `admin`

O deploy automático pode ficar ativado.

## Recuperação de senha
No Supabase → Authentication → URL Configuration:\n- Site URL: `https://dailywork-w4cw.onrender.com`\n- Redirect URLs: `https://dailywork-w4cw.onrender.com/redefinir.html`\n\nO app chama `resetPasswordForEmail` com essa URL. A página de redefinição atualiza a senha pela própria autenticação Supabase.
O app deve chamar `resetPasswordForEmail(email, { redirectTo: 'https://SEU-SITE.onrender.com/redefinir.html' })`.

## Métricas
- Contas: quantidade de perfis criados.
- Instalações: identificadores aleatórios de instalação enviados pelo app. É uma contagem de instalações registradas, não uma medição infalível de downloads da loja.
