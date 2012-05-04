#!/usr/bin/env escript
%%! -smp enable -sname test@localhost -setcookie test

main([]) ->
    {mbox,scala@localhost} ! self(),
    loop().

loop() ->
    receive
        {monitor, Pid} ->
            Ref = monitor(process, Pid),
            respond(Ref),
            loop();
        {demonitor, Ref} ->
            demonitor(Ref),
            respond({demonitor, Ref}),
            loop();
        {'DOWN', _, _, _, Reason}  ->
            respond({down, Reason}),
            loop();
        {exit, Reason} ->
            exit({exit, Reason})
    after 2000 ->
            respond(timeout)
    end.

respond(Msg) ->
    {scala,scala@localhost} ! Msg.


