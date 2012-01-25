#!/usr/bin/env escript
%%! -smp enable -sname test@localhost -setcookie test

main([]) ->
	register(receiver,self()),
	io:format("ready~n"),
	receive
		{One,Pid} -> 
			io:format("~p~n", [One]),
			Pid ! ok
	end,
	receive
		{Two,Pid} -> 
			io:format("~p~n", [Two]),
			Pid ! ok
	end,
	receive
		ok -> ok
	end.
