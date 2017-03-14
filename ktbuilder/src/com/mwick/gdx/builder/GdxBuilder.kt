package com.mwick.gdx.builder

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener

val skin : Skin? = null; // needed for compilation. TODO: figure out how to initialize this safely

inline fun <T> with(obj: T, action: T.() -> Unit) : T {
    obj.action()
    return obj
}

inline fun <T : Actor> WidgetGroup.build(elm: T, init: T.() -> Unit) : T {
    elm.init()
    if (this is Container<*>) {
        this.actor = elm
    } else if (this !is Table) {
        // Table is special, it ignores all but cells.
        // This allows you to use the WidgetGroup functions from table context, and pass them to cell(..).
        addActor(elm)
    }
    return elm
}

inline fun <T : Actor> T.build(init: T.() -> Unit) = with(this, init)

inline fun WidgetGroup.stack(init: Stack.() -> Unit) = build(Stack(), init)
inline fun WidgetGroup.table(init: Table.() -> Unit) = build(Table(), init)
inline fun WidgetGroup.horz(init: HorizontalGroup.() -> Unit) = build(HorizontalGroup(), init)
inline fun WidgetGroup.vert(init: VerticalGroup.() -> Unit) = build(VerticalGroup(), init)
inline fun <T : WidgetGroup> T.scroll(init: () -> Actor) = build(ScrollPane(init()), {})

inline fun WidgetGroup.button(text: String = "", init: Button.() -> Unit) = build(TextButton(text, skin), init)
inline fun WidgetGroup.label(text: String = "", init: Label.() -> Unit) = build(Label(text, skin), init)
inline fun WidgetGroup.label(text: String = "") = build(Label(text, skin), {})

inline fun Actor.onClick(crossinline action: () -> Unit) =
        addListener(
            object : ClickListener() {
                override fun clicked(e: InputEvent, x: Float, y: Float) { action(); }
            }
        )
inline fun Actor.onClickEvent(crossinline action: (InputEvent, Float, Float) -> Unit) =
        addListener(
                object : ClickListener() {
                    override fun clicked(e: InputEvent, x: Float, y: Float) { action(e, x, y); }
                }
        )


inline fun Table.cell(actor: Actor? = null) = add(actor)
inline fun Table.cell(actor: Actor? = null, init: Cell<*>.() -> Unit) = with(add(actor), init)
inline fun <A : Actor?> Cell<A>.style(init: Cell<A>.() -> Unit) = with(this, init)


fun main(args : Array<String>) {
    val buttons = listOf(Pair("First", "Post"), Pair("Second", "Wind"), Pair("Third", "Time"))

    val content = Table()
    content.build {
        scroll {
            table {
                cell(label("Action"))
                cell(label("Description"))
                row()
                for ((action, desc) in buttons) {
                    cell(button(action) {
                        onClick {
                            print("Clicked!")
                        }
                    }).pad(10f).left()
                    cell(label(desc)).padLeft(10f)
                    row()
                }
            }
        }
    }
}