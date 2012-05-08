#!/usr/bin/env escript
%%! -smp enable -sname test@localhost -setcookie test

main([]) ->
  register(echo, self()),
  io:format("ok~n"),
  receive
    {Pid, Msg} -> Pid ! Msg
  end.
