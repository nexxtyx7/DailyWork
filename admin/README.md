# DailyWork — configuração inicial do painel

O painel estático fica nesta pasta e usa o Supabase do projeto DailyWork.

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
Depois que o site estiver publicado, adicione a URL completa `https://SEU-SITE.onrender.com/redefinir.html` em Supabase → Authentication → URL Configuration → Redirect URLs.
O app deve chamar `resetPasswordForEmail(email, { redirectTo: 'https://SEU-SITE.onrender.com/redefinir.html' })`.

## Métricas
- Contas: quantidade de perfis criados.
- Instalações: identificadores aleatórios de instalação enviados pelo app. É uma contagem de instalações registradas, não uma medição infalível de downloads da loja.
