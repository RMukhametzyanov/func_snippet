package com.testviewer

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class FolderTreeToolWindowFactory : ToolWindowFactory {
    private var treePanel: FolderTreePanel? = null
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        treePanel = FolderTreePanel(project)
        val content = contentFactory.createContent(treePanel!!.component(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}

