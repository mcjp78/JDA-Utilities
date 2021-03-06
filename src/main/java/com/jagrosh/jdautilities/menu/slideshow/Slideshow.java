/*
 * Copyright 2016 John Grosh (jagrosh).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jdautilities.menu.slideshow;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import com.jagrosh.jdautilities.waiter.EventWaiter;
import com.jagrosh.jdautilities.menu.Menu;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.dv8tion.jda.core.requests.RestAction;

/**
 *
 * @author John Grosh
 */
public class Slideshow extends Menu {
    
    private final BiFunction<Integer,Integer,Color> color;
    private final BiFunction<Integer,Integer,String> text;
    private final BiFunction<Integer,Integer,String> description;
    private final boolean showPageNumbers;
    private final List<String> urls;
    private final Consumer<Message> finalAction;
    private final boolean waitOnSinglePage;
    
    public static final String LEFT = "\u25C0";
    public static final String STOP = "\u23F9";
    public static final String RIGHT = "\u25B6";
    
    protected Slideshow(EventWaiter waiter, Set<User> users, Set<Role> roles, long timeout, TimeUnit unit,
            BiFunction<Integer,Integer,Color> color, BiFunction<Integer,Integer,String> text, BiFunction<Integer,Integer,String> description,
            Consumer<Message> finalAction, boolean showPageNumbers, List<String> items, boolean waitOnSinglePage)
    {
        super(waiter, users, roles, timeout, unit);
        this.color = color;
        this.text = text;
        this.description = description;
        this.showPageNumbers = showPageNumbers;
        this.urls = items;
        this.finalAction = finalAction;
        this.waitOnSinglePage = waitOnSinglePage;
    }

    /**
     * Begins pagination on page 1 as a new {@link net.dv8tion.jda.core.entities.Message Message} 
     * in the provided {@link net.dv8tion.jda.core.entities.MessageChannel MessageChannel}.
     * 
     * @param  channel
     *         The MessageChannel to send the new Message to
     */
    @Override
    public void display(MessageChannel channel) {
        paginate(channel, 1);
    }

    /**
     * Begins pagination on page 1 displaying this Pagination by editing the provided 
     * {@link net.dv8tion.jda.core.entities.Message Message}.
     * 
     * @param  message
     *         The Message to display the Menu in
     */
    @Override
    public void display(Message message) {
        paginate(message, 1);
    }
    
    /**
     * Begins pagination as a new {@link net.dv8tion.jda.core.entities.Message Message} 
     * in the provided {@link net.dv8tion.jda.core.entities.MessageChannel MessageChannel}, starting
     * on whatever page number is provided.
     * 
     * @param  channel
     *         The MessageChannel to send the new Message to
     * @param  pageNum
     *         The page number to begin on
     */
    public void paginate(MessageChannel channel, int pageNum)
    {
        if(pageNum<1)
            pageNum = 1;
        else if (pageNum>urls.size())
            pageNum = urls.size();
        Message msg = renderPage(pageNum);
        initialize(channel.sendMessage(msg), pageNum);
    }
    
    /**
     * Begins pagination displaying this Pagination by editing the provided 
     * {@link net.dv8tion.jda.core.entities.Message Message}, starting on whatever
     * page number is provided.
     * 
     * @param  message
     *         The MessageChannel to send the new Message to
     * @param  pageNum
     *         The page number to begin on
     */
    public void paginate(Message message, int pageNum)
    {
        if(pageNum<1)
            pageNum = 1;
        else if (pageNum>urls.size())
            pageNum = urls.size();
        Message msg = renderPage(pageNum);
        initialize(message.editMessage(msg), pageNum);
    }
    
    private void initialize(RestAction<Message> action, int pageNum)
    {
        action.queue(m->{
            if(urls.size()>1)
            {
                m.addReaction(LEFT).queue();
                m.addReaction(STOP).queue();
                m.addReaction(RIGHT).queue(v -> pagination(m, pageNum), t -> pagination(m, pageNum));
            }
            else if(waitOnSinglePage)
            {
                m.addReaction(STOP).queue(v -> pagination(m, pageNum), t -> pagination(m, pageNum));
            }
            else
            {
                finalAction.accept(m);
            }
        });
    }
    
    private void pagination(Message message, int pageNum)
    {
        waiter.waitForEvent(MessageReactionAddEvent.class, (MessageReactionAddEvent event) -> {
            if(!event.getMessageId().equals(message.getId()))
                return false;
            if(!(LEFT.equals(event.getReaction().getEmote().getName()) 
                    || STOP.equals(event.getReaction().getEmote().getName())
                    || RIGHT.equals(event.getReaction().getEmote().getName())))
                return false;
            return isValidUser(event);
        }, event -> {
            int newPageNum = pageNum;
            switch(event.getReaction().getEmote().getName())
            {
                case LEFT:  if(newPageNum>1) newPageNum--; break;
                case RIGHT: if(newPageNum<urls.size()) newPageNum++; break;
                case STOP: finalAction.accept(message); return;
            }
            try{event.getReaction().removeReaction(event.getUser()).queue();}catch(PermissionException e){}
            int n = newPageNum;
            message.editMessage(renderPage(newPageNum)).queue(m -> {
                pagination(m, n);
            });
        }, timeout, unit, () -> finalAction.accept(message));
    }
    
    private Message renderPage(int pageNum)
    {
        MessageBuilder mbuilder = new MessageBuilder();
        EmbedBuilder ebuilder = new EmbedBuilder();
        ebuilder.setImage(urls.get(pageNum-1));
        ebuilder.setColor(color.apply(pageNum, urls.size()));
        ebuilder.setDescription(description.apply(pageNum, urls.size()));
        if(showPageNumbers)
            ebuilder.setFooter("Image "+pageNum+"/"+urls.size(), null);
        mbuilder.setEmbed(ebuilder.build());
        if(text!=null)
            mbuilder.append(text.apply(pageNum, urls.size()));
        return mbuilder.build();
    }
}
