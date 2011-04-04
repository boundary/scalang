#!/usr/bin/env escript
%%! -smp enable -sname test@localhost -setcookie test

main([]) ->
  process_flag(trap_exit, true),
  {mbox,scala@localhost} ! self(),
  receive
    {'EXIT', From, Reason} -> {scala, scala@localhost} ! Reason
  end.