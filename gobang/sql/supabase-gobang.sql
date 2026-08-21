-- 五子棋房间码联机 · 消息表初始化（spec4 v4.1 Plan B）
-- 用法：Supabase 控制台 → SQL Editor → 粘贴全部 → Run，仅需执行一次。
-- 与贪吃蛇排行榜(scores 表)互不影响；RLS 仅允许匿名读写本表。

create table if not exists gobang_msg (
  id         bigint generated always as identity primary key,
  room       text        not null,
  cid        text        not null,
  body       text        not null,
  created_at timestamptz not null default now()
);

create index if not exists gobang_msg_room_id_idx on gobang_msg (room, id);

alter table gobang_msg enable row level security;

drop policy if exists "gobang anon select" on gobang_msg;
create policy "gobang anon select" on gobang_msg
  for select to anon using (true);

drop policy if exists "gobang anon insert" on gobang_msg;
create policy "gobang anon insert" on gobang_msg
  for insert to anon with check (true);

drop policy if exists "gobang anon delete" on gobang_msg;
create policy "gobang anon delete" on gobang_msg
  for delete to anon using (true);
