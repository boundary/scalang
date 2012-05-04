#!/usr/bin/env escript
%%! -smp enable -sname test@localhost -setcookie test

main([]) ->
  process_flag(trap_exit, true),
  Pid = spawn_link(fun() ->
      process_flag(trap_exit, true),
      {mbox,scala@localhost} ! self(),
      receive
        {'EXIT', _From, Reason} -> {scala, scala@localhost} ! Reason;
        M -> exit(M)
      end
    end),
  receive
    {'EXIT', _, _} ->
      halt(),
      receive after infinity -> 0 end
  end.

