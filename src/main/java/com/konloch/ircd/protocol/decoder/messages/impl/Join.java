package com.konloch.ircd.protocol.decoder.messages.impl;

import com.konloch.ircd.server.channel.Channel;
import com.konloch.ircd.server.client.User;
import com.konloch.ircd.protocol.ProtocolMessage;
import com.konloch.ircd.extension.events.listeners.IRCdUserListener;
import com.konloch.ircd.protocol.decoder.messages.DecodeMessage;
import com.konloch.util.FastStringUtils;

import static com.konloch.ircd.protocol.encoder.messages.IRCOpcodes.RPL_JOIN;

/**
 * @author Konloch
 * @since 3/3/2023
 */
public class Join implements ProtocolMessage
{
	public static final Object CREATE_CHANNEL_LOCK = new Object();
	
	@Override
	public void run(User user, String msgVal)
	{
		if(msgVal == null || msgVal.isEmpty())
			return;
		
		//TODO check if msg val is ascii
		
		if(!user.isFlagHasSetInitialNick())
			user.setFlagHasSetInitialNick(true);
		
		for(IRCdUserListener listener : user.getIRC().getEvents().getUserEvents())
			if(!listener.onJoinChannel(user, msgVal))
				return;
		
		if(!msgVal.startsWith("#") || msgVal.length() < 2)
			return;
		
		final String[] channels = FastStringUtils.split(msgVal.replace(" ", ""), ",");
		
		for(String channelName : channels)
			joinChannel(user, channelName);
	}
	
	private void joinChannel(User user, String channelName)
	{
		Channel channel = user.getIRC().getChannels().get(channelName);
		
		if(channel == null)
		{
			for(IRCdUserListener listener : user.getIRC().getEvents().getUserEvents())
				if(!listener.onCreateChannel(user, channelName))
					return;
			
			synchronized (CREATE_CHANNEL_LOCK)
			{
				Channel createdChannel = new Channel(channelName);
				user.getIRC().getChannels().put(channelName, createdChannel);
				channel = createdChannel;
			}
		}
		
		//join the channel
		channel.getUsers().add(user);
		user.getChannels().add(channelName);
		
		//signal the join back to the client
		user.getEncoder().newUserMessage()
				.opcode(RPL_JOIN)
				.message(channelName)
				.send();
		
		//update the room list
		for(User other : channel.getUsers())
			DecodeMessage.NAMES.getDecodeRunnable().run(other, channelName);
	}
}
