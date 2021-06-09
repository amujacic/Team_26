package com.tugraz.chronos

import android.app.PendingIntent.getActivity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.Image
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.tugraz.chronos.model.entities.Task
import com.tugraz.chronos.model.entities.TaskGroupRelation
import com.tugraz.chronos.model.service.ChronosService
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


lateinit var chronosService: ChronosService

class TaskItemHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.task_recycler_item, parent, false)) {
    private var mTitle: TextView? = null
    private var mDate: TextView? = null


    init {
        mTitle = itemView.findViewById(R.id.tv_tri_title)
        mDate = itemView.findViewById(R.id.tv_tri_date)
    }

    fun bind(task: Task) {
        val title = task.title


        mTitle?.text = title
        mDate?.text = getTimeUntil(task, LocalDateTime.now())
    }
}

class ListAdapter(private var list: List<Task>, private var main : Context)
    : RecyclerView.Adapter<TaskItemHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskItemHolder {
        val inflater = LayoutInflater.from(parent.context)
        return TaskItemHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: TaskItemHolder, position: Int) {
        val task: Task = list[position]
        holder.bind(task)

        // button click logic
        val intent = Intent(Intent(main, TaskDetailsActivity::class.java))
        val b = Bundle()
        b.putInt("id", task.taskId.toInt())
        intent.putExtras(b) // Put id to intent

        holder.itemView.setOnClickListener {
           main.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = list.size
}

abstract class SwipeToDelete(context: Context, dragDir: Int, swipeDir: Int):
        ItemTouchHelper.SimpleCallback(dragDir, swipeDir) {
    override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }
}

fun getTimeUntil(task: Task, now: LocalDateTime): String {
    val date1 = LocalDateTime.parse(
        task.date,
        DateTimeFormatter.ISO_DATE_TIME
    )

    val input: Long = now.until(date1, ChronoUnit.SECONDS)

    val days = input / 86400
    val hours = (input % 86400 ) / 3600
    val minutes = ((input % 86400 ) % 3600 ) / 60
    val seconds = ((input % 86400 ) % 3600 ) % 60

    return days.toString() + "d " + hours.toString() + ":" + minutes.toString() + ":" + seconds.toString()
}

fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable) {
        return drawable.bitmap
    }
    val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var list_recycler_view: RecyclerView
    lateinit var task_list: List<Task>
    private val p = Paint()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initNavigationDrawer()

        chronosService = ChronosService(this)

        swipeRefreshLayout = findViewById(R.id.srl_ma)
        swipeRefreshLayout.setOnRefreshListener {
            loadGroups()

            val selectedGroupFromIntent = intent.getIntExtra("GROUP_ID", -1)
            if (selectedGroupFromIntent != -1) {
                task_list = sortTasks(chronosService.
                getTaskGroupById(selectedGroupFromIntent.toLong()).taskList)
            }
            else {
                task_list = sortTasks(chronosService.getAllTasks())
            }
            list_recycler_view.adapter = ListAdapter(task_list, this)
            (list_recycler_view.adapter as ListAdapter).notifyDataSetChanged()
            Handler(Looper.getMainLooper()).postDelayed({
                swipeRefreshLayout.isRefreshing = false
            }, 500)
        }

        loadGroups()
        val selectedGroupFromIntent = intent.getIntExtra("GROUP_ID", -1)
        if (selectedGroupFromIntent != -1) {
            task_list = sortTasks(chronosService.
            getTaskGroupById(selectedGroupFromIntent.toLong()).taskList)
        }
        else {
            task_list = sortTasks(chronosService.getAllTasks())
        }
        list_recycler_view = findViewById(R.id.rv_ma)
        list_recycler_view.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ListAdapter(task_list,this@MainActivity)

        }

        val item = object : SwipeToDelete(this, 0, ItemTouchHelper.LEFT){
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                chronosService.deleteTask(task_list[viewHolder.adapterPosition])
                task_list = sortTasks(chronosService.getAllTasks())
                list_recycler_view.adapter = ListAdapter(task_list,this@MainActivity)
                (list_recycler_view.adapter as ListAdapter).notifyDataSetChanged()
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val icon: Bitmap
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val height = itemView.bottom.toFloat() - itemView.top.toFloat()
                    val width = height / 3
                    p.color = Color.parseColor("#D32F2F")
                    val background = RectF(itemView.right.toFloat() + (dX / 4), itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                    c.drawRect(background, p)
                    val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_delete, theme)
                    icon = drawableToBitmap(d!!)!!
                    val icon_dest = RectF(itemView.right.toFloat() - 2 * width, itemView.top.toFloat() + width, itemView.right.toFloat() - width, itemView.bottom.toFloat() - width)
                    c.drawBitmap(icon, null, icon_dest, p)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX / 4, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(item).attachToRecyclerView(list_recycler_view)

        val item_2 = object : SwipeToDelete(this, 1, ItemTouchHelper.RIGHT){
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                CreateTaskActivity.setEditOrCreate(task_list[viewHolder.adapterPosition])
                startActivity(Intent(this@MainActivity, CreateTaskActivity::class.java))
                task_list = sortTasks(chronosService.getAllTasks())
                list_recycler_view.adapter = ListAdapter(task_list, this@MainActivity)
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val icon: Bitmap
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val height = itemView.bottom.toFloat() - itemView.top.toFloat()
                    val width = height / 3
                    p.color = Color.parseColor("#F9D71C")
                    val background = RectF(itemView.left.toFloat() + (dX / 4), itemView.top.toFloat(), itemView.left.toFloat(), itemView.bottom.toFloat())
                    c.drawRect(background, p)
                    val d = ResourcesCompat.getDrawable(resources, R.drawable.ic_delete, theme)
                    icon = drawableToBitmap(d!!)!!
                    val icon_dest = RectF(itemView.left.toFloat() - 2 * width, itemView.top.toFloat() + width, itemView.left.toFloat() - width, itemView.bottom.toFloat() - width)
                    c.drawBitmap(icon, null, icon_dest, p)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX / 4, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(item_2).attachToRecyclerView(list_recycler_view)

        val fab = findViewById<FloatingActionButton>(R.id.btn_ma_add)
        fab.setOnClickListener {
            CreateTaskActivity.setEditOrCreate(null)
            startActivity(Intent(this, CreateTaskActivity::class.java))
            finish()
        }
    }

    fun sortTasks(task_list: List<Task>): List<Task> {
        return task_list.sortedBy { value -> value.date }
    }

    fun loadGroups() {
        val group_list = chronosService.getAllGroups()
        var item_count = 1

        val navigationView = findViewById<NavigationView>(R.id.nav_view)

        val menu = navigationView.menu
        menu.removeGroup(1)

        val map = HashMap<TaskGroupRelation, Task> ()
        val groups_without_tasks = mutableListOf<TaskGroupRelation>()
        for (group in group_list) {
            if(group.taskList.isNotEmpty()){
                val upcoming_task = sortTasks(group.taskList)[0]
                map[group] = upcoming_task
            } else {
                groups_without_tasks.add(group)
            }
        }

        val groups_sorted_by_upcoming_task = map.toList().sortedBy { (_, value) -> value.date}.toMap()

        for (group_task in groups_sorted_by_upcoming_task) {

            val date1 = LocalDateTime.parse(
                group_task.value.date,
                DateTimeFormatter.ISO_DATE_TIME
            )
            val date2 = LocalDateTime.now()

            val input: Long = date2.until(date1, ChronoUnit.SECONDS)

            val days = input / 86400
            val hours = (input % 86400 ) / 3600
            val minutes = ((input % 86400 ) % 3600 ) / 60
            val seconds = ((input % 86400 ) % 3600 ) % 60
            val timeUntil = days.toString() + "d " + hours.toString() + ":" + minutes.toString() + ":" + seconds.toString()

            val text = group_task.key.taskGroup.title + "\n" + group_task.value.title + " - " + timeUntil
            menu.add(1, group_task.key.taskGroup.taskGroupId.toInt(), item_count, text);
            val menu_item : MenuItem = menu.findItem(group_task.key.taskGroup.taskGroupId.toInt());
            menu_item.setActionView(ImageButton(this));
            (menu_item.actionView as ImageButton).setImageResource(R.drawable.ic_edit)
            menu_item.actionView.setOnClickListener {
                val intent = Intent(this, CreateGroupActivity::class.java)
                intent.putExtra("GROUP_ID", group_task.key.taskGroup.taskGroupId)
                startActivity(intent)
            }
            item_count++
        }
         for(group in groups_without_tasks){
             menu.add(1, group.taskGroup.taskGroupId.toInt(), item_count, group.taskGroup.title)
             val menu_item : MenuItem = menu.findItem(group.taskGroup.taskGroupId.toInt());
             menu_item.setActionView(ImageButton(this));
             (menu_item.actionView as ImageButton).setImageResource(R.drawable.ic_edit)
             menu_item.actionView.setOnClickListener {
                 val intent = Intent(this, CreateGroupActivity::class.java)
                 intent.putExtra("GROUP_ID", group.taskGroup.taskGroupId)
                 startActivity(intent)
             }
             item_count++
        }
    }

    private fun initNavigationDrawer() {
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        toggle.syncState()

        val navigationView = findViewById<NavigationView>(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)
    }

    // used for create Group Button in drawer menu
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.createGroup -> {
                startActivity(Intent(this, CreateGroupActivity::class.java))
            }
            R.id.options_button -> {
                startActivity(Intent(this, OptionsActivity::class.java))
            }
            else -> {
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("GROUP_ID", item.itemId)
                startActivity(intent)
            }
        }
        return true
    }

    // used for closing the drawer menu
    override fun onBackPressed() {
        val drawer: DrawerLayout = findViewById(R.id.drawer_layout)
        if(drawer.isDrawerOpen(GravityCompat.START)){
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}